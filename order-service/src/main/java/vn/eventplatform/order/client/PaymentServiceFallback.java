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
}
