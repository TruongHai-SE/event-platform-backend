package vn.eventplatform.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.payment.client.dto.InternalOrderResponse;
import vn.eventplatform.payment.client.dto.OrderMarkPaidRequest;

import java.util.UUID;

@FeignClient(name = "order-service", fallback = OrderServiceFallback.class)
public interface OrderServiceClient {

    @GetMapping("/internal/v1/orders/{id}")
    ApiResponse<InternalOrderResponse> getOrderById(
            @RequestHeader("X-Internal-Secret") String secret,
            @PathVariable("id") UUID id
    );

    @GetMapping("/internal/v1/orders/code/{orderCode}")
    ApiResponse<InternalOrderResponse> getOrderByCode(
            @RequestHeader("X-Internal-Secret") String secret,
            @PathVariable("orderCode") String orderCode
    );

    @PostMapping("/internal/v1/orders/{id}/paid")
    ApiResponse<InternalOrderResponse> markOrderPaid(
            @RequestHeader("X-Internal-Secret") String secret,
            @PathVariable("id") UUID id,
            @RequestBody OrderMarkPaidRequest request
    );
}
