package vn.eventplatform.payment.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.payment.dto.InternalRetryPaymentRequest;
import vn.eventplatform.payment.dto.PaymentInitResponse;
import vn.eventplatform.payment.service.PaymentService;

@Slf4j
@Hidden
@RestController
@RequestMapping("/internal/v1/payments")
@RequiredArgsConstructor
public class InternalPaymentController {

    private final PaymentService paymentService;

    @Value("${security.internal.secret}")
    private String internalSecret;

    @PostMapping("/retry")
    public ResponseEntity<ApiResponse<PaymentInitResponse>> retryPayment(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody InternalRetryPaymentRequest request
    ) {
        validateInternalSecret(secret);
        log.info("Nhận yêu cầu thử lại thanh toán (đổi cổng) qua internal API: orderId={}, orderCode={}, method={}",
                request.getOrderId(), request.getOrderCode(), request.getMethod());
        PaymentInitResponse response = paymentService.retryPayment(request);
        return ResponseEntity.ok(ApiResponse.ok("Khởi tạo lại liên kết thanh toán thành công", response));
    }

    @PostMapping("/refund")
    public ResponseEntity<ApiResponse<vn.eventplatform.payment.dto.RefundResponse>> refundByOrder(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody vn.eventplatform.payment.dto.InternalRefundOrderRequest request
    ) {
        validateInternalSecret(secret);
        log.info("Nhận yêu cầu hoàn tiền đơn hàng qua internal API: orderId={}, amount={}, reason={}",
                request.getOrderId(), request.getAmount(), request.getReason());
        vn.eventplatform.payment.dto.RefundResponse response = paymentService.refundByOrder(request);
        return ResponseEntity.ok(ApiResponse.ok("Xử lý yêu cầu hoàn tiền thành công", response));
    }

    @PostMapping("/refunds/batch")
    public ResponseEntity<ApiResponse<vn.eventplatform.payment.dto.BatchRefundResponse>> batchRefundByOrders(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody vn.eventplatform.payment.dto.BatchRefundByOrdersRequest request
    ) {
        validateInternalSecret(secret);
        log.info("Nhận yêu cầu hoàn tiền hàng loạt theo danh sách đơn hàng qua internal API: totalCount={}",
                request.getRequests() != null ? request.getRequests().size() : 0);
        vn.eventplatform.payment.dto.BatchRefundResponse response = paymentService.batchRefundByOrders(request);
        return ResponseEntity.ok(ApiResponse.ok("Xử lý hoàn tiền hàng loạt hoàn tất", response));
    }

    private void validateInternalSecret(String secret) {
        if (secret == null || !secret.equals(internalSecret)) {
            log.warn("Gọi InternalPaymentController với secret không hợp lệ!");
            throw new AppException(ErrorCode.FORBIDDEN, "Invalid internal secret");
        }
    }
}
