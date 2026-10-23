package vn.eventplatform.ticket.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.ticket.client.dto.InternalOrderResponse;

import java.util.UUID;

@FeignClient(name = "order-service", fallback = OrderServiceFallback.class)
public interface OrderServiceClient {

    @GetMapping("/internal/v1/orders/{id}")
    ApiResponse<InternalOrderResponse> getOrderById(
            @RequestHeader("X-Internal-Secret") String secret,
            @PathVariable("id") UUID id
    );
}
