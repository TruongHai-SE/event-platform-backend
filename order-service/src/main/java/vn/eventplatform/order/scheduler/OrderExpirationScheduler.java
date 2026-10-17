package vn.eventplatform.order.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.eventplatform.order.service.OrderService;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "order.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpirationScheduler {

    private final OrderService orderService;
    private final RedissonClient redissonClient;

    @Value("${order.scheduler.lock-key:lock:scheduler:order-expiration}")
    private String lockKey = "lock:scheduler:order-expiration";

    @Value("${order.scheduler.lock-lease-time-seconds:25}")
    private long lockLeaseTimeSeconds = 25;

    @Scheduled(
            fixedDelayString = "${order.expiration-check-interval-ms:30000}",
            initialDelayString = "${order.expiration-initial-delay-ms:10000}"
    )
    public void scanAndReleaseExpiredOrders() {
        RLock lock = null;
        boolean acquired = false;
        try {
            if (redissonClient != null) {
                try {
                    lock = redissonClient.getLock(lockKey);
                    // tryLock với waitTime = 0s: nếu có instance khác đang chạy thì bỏ qua ngay lập tức (idempotent per tick)
                    acquired = lock.tryLock(0, lockLeaseTimeSeconds, TimeUnit.SECONDS);
                    if (!acquired) {
                        log.debug("Instance khác đang thực thi quét đơn hàng hết hạn (lockKey={}), bỏ qua chu kỳ này", lockKey);
                        return;
                    }
                } catch (Exception e) {
                    log.error("Không thể lấy Redis distributed lock cho scheduler (lockKey={}): {}. Bỏ qua chu kỳ này để đảm bảo an toàn phân tán.", lockKey, e.getMessage());
                    return;
                }
            }

            int releasedCount = orderService.releaseExpiredOrders();
            if (releasedCount > 0) {
                log.info("Đã quét và giải phóng tồn kho cho {} đơn hàng hết hạn giữ chỗ", releasedCount);
            }
        } catch (Exception e) {
            log.error("Lỗi trong quá trình quét giải phóng đơn hàng hết hạn", e);
        } finally {
            if (acquired && lock != null) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception e) {
                    log.warn("Lỗi khi giải phóng lock scheduler (lockKey={}): {}", lockKey, e.getMessage());
                }
            }
        }
    }
}
