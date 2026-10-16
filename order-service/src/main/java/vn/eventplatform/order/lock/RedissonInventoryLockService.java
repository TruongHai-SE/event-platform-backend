package vn.eventplatform.order.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedissonInventoryLockService implements InventoryLockService {

    private final RedissonClient redissonClient;

    @Value("${app.lock.wait-time-seconds:5}")
    private long waitTimeSeconds = 5;

    @Value("${app.lock.lease-time-seconds:-1}")
    private long leaseTimeSeconds = -1;

    private static final String LOCK_KEY_PATTERN = "lock:inventory:showtime:%s:ticket-type:%s";

    @Override
    public <T> T executeWithLock(UUID showTimeId, Collection<UUID> ticketTypeIds, Supplier<T> action) {
        if (showTimeId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "ID suất diễn không được để trống khi lock tồn kho");
        }
        if (ticketTypeIds == null || ticketTypeIds.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Danh sách hạng vé không được để trống khi lock tồn kho");
        }

        // Loại bỏ null, loại bỏ trùng lặp và sắp xếp theo thứ tự xác định để chống deadlock (ABBA deadlock)
        List<UUID> sortedTicketTypeIds = ticketTypeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        if (sortedTicketTypeIds.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không có hạng vé hợp lệ để lock tồn kho");
        }

        List<String> lockKeys = sortedTicketTypeIds.stream()
                .map(ticketTypeId -> String.format(LOCK_KEY_PATTERN, showTimeId, ticketTypeId))
                .toList();

        RLock lock;
        if (lockKeys.size() == 1) {
            lock = redissonClient.getLock(lockKeys.get(0));
        } else {
            RLock[] locks = lockKeys.stream()
                    .map(redissonClient::getLock)
                    .toArray(RLock[]::new);
            lock = redissonClient.getMultiLock(locks);
        }

        boolean acquired = false;
        try {
            log.debug("Đang chờ lấy Redis lock cho showTimeId={}, keys={}", showTimeId, lockKeys);
            if (leaseTimeSeconds > 0) {
                acquired = lock.tryLock(waitTimeSeconds, leaseTimeSeconds, TimeUnit.SECONDS);
            } else {
                acquired = lock.tryLock(waitTimeSeconds, TimeUnit.SECONDS);
            }

            if (!acquired) {
                log.warn("Không thể lấy Redis lock sau {}s cho keys={}", waitTimeSeconds, lockKeys);
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Hệ thống đang có nhiều lượt giữ chỗ cùng lúc cho hạng vé này. Vui lòng thử lại sau giây lát.");
            }

            log.debug("Đã lấy thành công Redis lock cho keys={}", lockKeys);
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Tiến trình bị gián đoạn khi đang chờ lấy Redis lock", e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Giao dịch giữ chỗ vé bị gián đoạn, vui lòng thử lại");
        } finally {
            if (acquired) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        log.debug("Đã giải phóng Redis lock cho keys={}", lockKeys);
                    }
                } catch (Exception e) {
                    log.error("Lỗi khi giải phóng Redis lock cho keys={}", lockKeys, e);
                }
            }
        }
    }

    @Override
    public void executeWithLock(UUID showTimeId, Collection<UUID> ticketTypeIds, Runnable action) {
        executeWithLock(showTimeId, ticketTypeIds, () -> {
            action.run();
            return null;
        });
    }
}
