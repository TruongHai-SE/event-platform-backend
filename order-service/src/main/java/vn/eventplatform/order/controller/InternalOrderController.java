package vn.eventplatform.order.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.order.dto.OrderMarkPaidRequest;
import vn.eventplatform.order.dto.OrderResponse;
import vn.eventplatform.order.service.OrderService;

import java.util.UUID;

@Slf4j
@Hidden
@RestController
@RequestMapping("/internal/v1/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    @Value("${security.internal.secret}")
    private String internalSecret;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable("id") UUID id
    ) {
        validateInternalSecret(secret);
        OrderResponse response = orderService.getInternalOrderById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/code/{orderCode}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderByCode(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable("orderCode") String orderCode
    ) {
        validateInternalSecret(secret);
        OrderResponse response = orderService.getInternalOrderByCode(orderCode);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/paid")
    public ResponseEntity<ApiResponse<OrderResponse>> markOrderPaid(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable("id") UUID id,
            @RequestBody OrderMarkPaidRequest request
    ) {
        validateInternalSecret(secret);
        log.info("Nhận yêu cầu đánh dấu đơn hàng đã thanh toán qua internal API: orderId={}, paymentId={}", id, request.getPaymentId());
        OrderResponse response = orderService.markOrderPaid(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật đơn hàng sang trạng thái PAID", response));
    }

    private void validateInternalSecret(String secret) {
        if (secret == null || !secret.equals(internalSecret)) {
            log.warn("Gọi InternalOrderController với secret không hợp lệ!");
            throw new AppException(ErrorCode.FORBIDDEN, "Invalid internal secret");
        }
    }
}
