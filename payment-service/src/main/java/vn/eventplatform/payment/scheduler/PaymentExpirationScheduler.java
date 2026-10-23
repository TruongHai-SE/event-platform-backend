package vn.eventplatform.payment.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.eventplatform.payment.service.PaymentService;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentExpirationScheduler {

    private final PaymentService paymentService;

    @Scheduled(
            fixedDelayString = "${payment.scheduler.expiration-check-interval-ms:30000}",
            initialDelayString = "${payment.scheduler.expiration-initial-delay-ms:10000}"
    )
    public void scanAndCancelExpiredPayments() {
        try {
            int count = paymentService.cancelExpiredPendingPayments();
            if (count > 0) {
                log.info("Đã quét và hủy {} giao dịch thanh toán quá hạn 15 phút", count);
            }
        } catch (Exception e) {
            log.error("Lỗi trong quá trình quét hủy giao dịch thanh toán quá hạn", e);
        }
    }
}
