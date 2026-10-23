package vn.eventplatform.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import vn.eventplatform.common.dto.ApiResponse;

import java.util.UUID;

@FeignClient(name = "ticket-service", fallback = TicketServiceFallback.class)
public interface TicketServiceClient {

    @GetMapping("/internal/v1/tickets/orders/{orderId}/has-used")
    ApiResponse<Boolean> hasUsedTickets(
            @RequestHeader("X-Internal-Secret") String secret,
            @PathVariable("orderId") UUID orderId
    );

    @PostMapping("/internal/v1/tickets/orders/{orderId}/cancel")
    ApiResponse<Integer> cancelTicketsByOrderId(
            @RequestHeader("X-Internal-Secret") String secret,
            @PathVariable("orderId") UUID orderId,
            @RequestParam(value = "reason", required = false) String reason
    );
}
