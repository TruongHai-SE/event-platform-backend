package vn.eventplatform.order.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.eventplatform.common.dto.ApiResponse;

import java.util.UUID;

@Slf4j
@Component
public class TicketServiceFallback implements TicketServiceClient {

    @Override
    public ApiResponse<Boolean> hasUsedTickets(String secret, UUID orderId) {
        log.warn("[TicketServiceFallback] hasUsedTickets called for orderId={}", orderId);
        return ApiResponse.ok(false);
    }

    @Override
    public ApiResponse<Integer> cancelTicketsByOrderId(String secret, UUID orderId, String reason) {
        log.warn("[TicketServiceFallback] cancelTicketsByOrderId called for orderId={}, reason={}", orderId, reason);
        return ApiResponse.ok(0);
    }
}
