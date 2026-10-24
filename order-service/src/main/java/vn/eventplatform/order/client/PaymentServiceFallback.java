package vn.eventplatform.order.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.order.client.dto.InternalRetryPaymentRequest;
import vn.eventplatform.order.dto.OrderPaymentInitResponse;

@Slf4j
@Component
public class PaymentServiceFallback implements PaymentServiceClient {

    @Override
    public ApiResponse<OrderPaymentInitResponse> retryPayment(String secret, InternalRetryPaymentRequest request) {
        log.warn("[PaymentServiceFallback] Không thể kết nối tới payment-service để thử lại thanh toán: orderId={}, method={}",
                request != null ? request.getOrderId() : "null",
                request != null ? request.getMethod() : "null");
        throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Dịch vụ thanh toán hiện không khả dụng, vui lòng thử lại sau");
    }

    @Override
    public ApiResponse<vn.eventplatform.order.client.dto.RefundResponse> refundByOrder(
            String secret, vn.eventplatform.order.client.dto.InternalRefundOrderRequest request) {
        log.warn("[PaymentServiceFallback] Không thể kết nối tới payment-service để hoàn tiền: orderId={}, amount={}",
                request != null ? request.getOrderId() : "null",
                request != null ? request.getAmount() : "null");
        // Fallback: return processing response
        return ApiResponse.ok(vn.eventplatform.order.client.dto.RefundResponse.builder()
                .orderId(request != null ? request.getOrderId() : null)
                .amount(request != null ? request.getAmount() : java.math.BigDecimal.ZERO)
                .status("PROCESSING")
                .reason(request != null ? request.getReason() : null)
                .build());
    }

    @Override
    public ApiResponse<vn.eventplatform.order.client.dto.BatchRefundResponse> batchRefundByOrders(
            String secret, vn.eventplatform.order.client.dto.BatchRefundByOrdersRequest request) {
        log.warn("[PaymentServiceFallback] Không thể kết nối tới payment-service để hoàn tiền hàng loạt: totalCount={}",
                request != null && request.getRequests() != null ? request.getRequests().size() : 0);
        int total = request != null && request.getRequests() != null ? request.getRequests().size() : 0;
        return ApiResponse.ok(vn.eventplatform.order.client.dto.BatchRefundResponse.builder()
                .totalRequested(total)
                .totalSuccess(0)
                .totalFailed(total)
                .totalRefundedAmount(java.math.BigDecimal.ZERO)
                .build());
    }
}
