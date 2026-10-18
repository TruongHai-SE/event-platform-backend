package vn.eventplatform.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.payment.dto.*;
import vn.eventplatform.payment.payment.dto.PaymentCallbackResult;
import vn.eventplatform.payment.service.PaymentService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment API", description = "Các API phục vụ khởi tạo liên kết thanh toán, tra cứu và xử lý callback từ các cổng thanh toán")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Khởi tạo thanh toán cho đơn hàng", description = "Tạo bản ghi payment PENDING và trả về URL/QR thanh toán tương ứng với cổng thanh toán (VNPAY, MOMO, SEPAY)")
    public ResponseEntity<ApiResponse<PaymentInitResponse>> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String clientIp = extractClientIp(httpServletRequest);
        PaymentInitResponse response = paymentService.createPayment(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/vnpay/callback")
    @Operation(summary = "Nhận kết quả callback/IPN từ cổng thanh toán VNPay", description = "Xác thực chữ ký số HMAC-SHA512 và cập nhật trạng thái đơn hàng sang PAID nếu thành công")
    public ResponseEntity<ApiResponse<PaymentCallbackResult>> handleVnPayCallback(
            @RequestParam Map<String, String> allParams
    ) {
        log.info("Nhận callback từ VNPay: {}", allParams);
        PaymentCallbackResult result = paymentService.handleVnPayCallback(allParams);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết giao dịch thanh toán theo ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(
            @PathVariable("id") UUID id
    ) {
        PaymentResponse response = paymentService.getPaymentById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Lấy danh sách các giao dịch thanh toán của một đơn hàng")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPaymentsByOrderId(
            @PathVariable("orderId") UUID orderId
    ) {
        List<PaymentResponse> responses = paymentService.getPaymentsByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    @PostMapping("/refund")
    @Operation(summary = "Tạo yêu cầu hoàn tiền cho giao dịch thanh toán")
    public ResponseEntity<ApiResponse<RefundResponse>> createRefund(
            @Valid @RequestBody CreateRefundRequest request
    ) {
        RefundResponse response = paymentService.createRefund(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.trim().isEmpty()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
