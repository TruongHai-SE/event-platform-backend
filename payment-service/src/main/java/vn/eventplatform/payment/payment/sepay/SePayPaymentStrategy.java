package vn.eventplatform.payment.payment.sepay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.eventplatform.payment.domain.PaymentGateway;
import vn.eventplatform.payment.domain.RefundStatus;
import vn.eventplatform.payment.payment.PaymentContext;
import vn.eventplatform.payment.payment.dto.PaymentCallbackResult;
import vn.eventplatform.payment.payment.dto.PaymentInitResult;
import vn.eventplatform.payment.payment.dto.RefundContext;
import vn.eventplatform.payment.payment.dto.RefundResult;
import vn.eventplatform.payment.payment.sepay.config.SePayProperties;
import vn.eventplatform.payment.payment.strategy.PaymentStrategy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SePayPaymentStrategy implements PaymentStrategy {

    private final SePayProperties sePayProperties;

    @Override
    public PaymentGateway getGateway() {
        return PaymentGateway.SEPAY;
    }

    @Override
    public PaymentInitResult createPaymentUrl(PaymentContext context) {
        log.info("Bắt đầu tạo SePay VietQR cho orderCode: {}, amount: {}", context.getOrderCode(), context.getAmount());

        // Chuẩn hóa mã thanh toán: xóa dấu '-' để khớp với quy tắc nhận diện của SePay (ví dụ: TKZ-20261018-ABC12 -> TKZ20261018ABC12)
        String paymentCode = context.getOrderCode() != null ? context.getOrderCode().replace("-", "") : "";
        long amountLong = context.getAmount().longValue();

        String encodedDes = URLEncoder.encode(paymentCode, StandardCharsets.UTF_8);
        String qrUrl = String.format("%s?acc=%s&bank=%s&amount=%d&des=%s&template=%s",
                sePayProperties.getQrBaseUrl(),
                sePayProperties.getAccountNumber(),
                sePayProperties.getBankName(),
                amountLong,
                encodedDes,
                sePayProperties.getQrTemplate());

        log.info("Đã tạo SePay VietQR URL: {}", qrUrl);

        return PaymentInitResult.builder()
                .paymentId(context.getPaymentId())
                .orderId(context.getOrderId())
                .method(PaymentGateway.SEPAY)
                .amount(context.getAmount())
                .paymentUrl(qrUrl)
                .qrCode(qrUrl)
                .build();
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> callbackParams) {
        return PaymentCallbackResult.builder()
                .success(true)
                .method(PaymentGateway.SEPAY)
                .build();
    }

    @Override
    public RefundResult refund(RefundContext context) {
        log.warn("Chức năng hoàn tiền SePay tự động qua API đang trong giai đoạn phát triển: refundLogId={}", context.getRefundLogId());
        return RefundResult.builder()
                .success(false)
                .refundLogId(context.getRefundLogId())
                .status(RefundStatus.PROCESSING)
                .message("Hệ thống đã ghi nhận yêu cầu hoàn tiền SePay. Vui lòng thực hiện hoàn tiền thủ công qua tài khoản ngân hàng")
                .refundedAmount(context.getAmount())
                .refundedAt(null)
                .build();
    }
}
