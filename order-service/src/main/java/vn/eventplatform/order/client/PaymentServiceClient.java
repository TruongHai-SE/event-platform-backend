package vn.eventplatform.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.order.client.dto.InternalRetryPaymentRequest;
import vn.eventplatform.order.dto.OrderPaymentInitResponse;

@FeignClient(name = "payment-service", fallback = PaymentServiceFallback.class)
public interface PaymentServiceClient {

    @PostMapping("/internal/v1/payments/retry")
    ApiResponse<OrderPaymentInitResponse> retryPayment(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody InternalRetryPaymentRequest request
    );

    @PostMapping("/internal/v1/payments/refund")
    ApiResponse<vn.eventplatform.order.client.dto.RefundResponse> refundByOrder(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody vn.eventplatform.order.client.dto.InternalRefundOrderRequest request
    );
}
