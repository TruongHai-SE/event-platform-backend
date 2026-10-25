package vn.eventplatform.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.payment.dto.*;
import vn.eventplatform.payment.dto.sepay.SepayWebhookRequest;
import vn.eventplatform.payment.dto.sepay.SepayWebhookResponse;
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
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Khởi tạo thanh toán cho đơn hàng", description = "Tạo bản ghi payment PENDING và trả về URL/QR thanh toán tương ứng với cổng thanh toán (VNPAY, SEPAY) dựa trên orderCode hoặc orderId")
    public ResponseEntity<ApiResponse<PaymentInitResponse>> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            Authentication authentication,
            HttpServletRequest httpServletRequest
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdminOrStaff = checkAdminOrStaff(authentication);
        String clientIp = extractClientIp(httpServletRequest);
        PaymentInitResponse response = paymentService.createPayment(request, currentUserId, isAdminOrStaff, clientIp);
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

    @PostMapping("/sepay/webhook")
    @Operation(summary = "Nhận webhook thông báo giao dịch từ cổng thanh toán SePay", description = "Xác thực API Key, bóc tách mã thanh toán và cập nhật đơn hàng thành công")
    public ResponseEntity<SepayWebhookResponse> handleSepayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody SepayWebhookRequest request
    ) {
        log.info("Nhận Webhook từ SePay: id={}, code={}, amount={}, content={}",
                request.getId(), request.getCode(), request.getTransferAmount(), request.getContent());
        SepayWebhookResponse response = paymentService.handleSepayWebhook(authHeader, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy chi tiết giao dịch thanh toán theo ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(
            @PathVariable("id") UUID id,
            Authentication authentication
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdminOrStaff = checkAdminOrStaff(authentication);
        PaymentResponse response = paymentService.getPaymentById(id, currentUserId, isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy danh sách các giao dịch thanh toán của một đơn hàng theo ID")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPaymentsByOrderId(
            @PathVariable("orderId") UUID orderId,
            Authentication authentication
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdminOrStaff = checkAdminOrStaff(authentication);
        List<PaymentResponse> responses = paymentService.getPaymentsByOrderId(orderId, currentUserId, isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    @GetMapping("/order-code/{orderCode}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy danh sách các giao dịch thanh toán của một đơn hàng theo mã đơn hàng")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPaymentsByOrderCode(
            @PathVariable("orderCode") String orderCode,
            Authentication authentication
    ) {
        UUID currentUserId = extractUserId(authentication);
        boolean isAdminOrStaff = checkAdminOrStaff(authentication);
        List<PaymentResponse> responses = paymentService.getPaymentsByOrderCode(orderCode, currentUserId, isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    @PostMapping("/refund")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo yêu cầu hoàn tiền cho giao dịch thanh toán")
    public ResponseEntity<ApiResponse<RefundResponse>> createRefund(
            @Valid @RequestBody CreateRefundRequest request
    ) {
        RefundResponse response = paymentService.createRefund(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping("/refunds/{refundLogId}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xác nhận hoàn tiền thủ công qua ngân hàng (chuyển MANUAL_BANK/PROCESSING/FAILED sang SUCCESS)")
    public ResponseEntity<ApiResponse<RefundResponse>> confirmManualRefund(
            @PathVariable UUID refundLogId,
            @Valid @RequestBody ConfirmManualRefundRequest request
    ) {
        RefundResponse response = paymentService.confirmManualRefund(refundLogId, request);
        return ResponseEntity.ok(ApiResponse.ok("Xác nhận hoàn tiền thủ công thành công", response));
    }

    @PostMapping("/refunds/{refundLogId}/switch-to-manual")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Chuyển yêu cầu hoàn tiền sang MANUAL_BANK để kế toán xử lý thủ công qua ngân hàng")
    public ResponseEntity<ApiResponse<RefundResponse>> switchRefundToManualBank(
            @PathVariable UUID refundLogId,
            @RequestBody(required = false) SwitchToManualRefundRequest request
    ) {
        String note = request != null ? request.getNote() : null;
        RefundResponse response = paymentService.switchRefundToManualBank(refundLogId, note);
        return ResponseEntity.ok(ApiResponse.ok("Chuyển trạng thái sang MANUAL_BANK thành công", response));
    }

    @GetMapping("/refunds/manual-bank")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy danh sách các yêu cầu hoàn tiền đang chờ chuyển khoản thủ công (MANUAL_BANK)")
    public ResponseEntity<ApiResponse<List<RefundResponse>>> getManualBankRefunds() {
        List<RefundResponse> responses = paymentService.getManualBankRefunds();
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    @GetMapping("/refunds/payment/{paymentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xem lịch sử hoàn tiền của giao dịch thanh toán")
    public ResponseEntity<ApiResponse<List<RefundResponse>>> getRefundsByPaymentId(@PathVariable UUID paymentId) {
        List<RefundResponse> responses = paymentService.getRefundsByPaymentId(paymentId);
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    @PostMapping("/refunds/batch")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thực hiện hoàn tiền hàng loạt theo chunk và rate limit",
            description = "Chia nhỏ danh sách yêu cầu hoàn tiền thành từng chunk và áp dụng rate limit điều tiết tải sang cổng thanh toán")
    public ResponseEntity<ApiResponse<BatchRefundResponse>> batchRefund(
            @Valid @RequestBody BatchRefundRequest request
    ) {
        BatchRefundResponse response = paymentService.batchRefund(request);
        return ResponseEntity.ok(ApiResponse.ok("Xử lý hoàn tiền hàng loạt hoàn tất", response));
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean checkAdminOrStaff(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_STAFF"));
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
