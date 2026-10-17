package vn.eventplatform.order.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.eventplatform.order.service.OrderService;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "order.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpirationScheduler {

    private final OrderService orderService;

    @Scheduled(
            fixedDelayString = "${order.expiration-check-interval-ms:30000}",
            initialDelayString = "${order.expiration-initial-delay-ms:10000}"
    )
    public void scanAndReleaseExpiredOrders() {
        try {
            int releasedCount = orderService.releaseExpiredOrders();
            if (releasedCount > 0) {
                log.info("Đã quét và giải phóng tồn kho cho {} đơn hàng hết hạn giữ chỗ", releasedCount);
            }
        } catch (Exception e) {
            log.error("Lỗi trong quá trình quét giải phóng đơn hàng hết hạn", e);
        }
    }
}
