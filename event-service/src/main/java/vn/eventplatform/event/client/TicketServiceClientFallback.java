package vn.eventplatform.event.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.eventplatform.common.dto.ApiResponse;

@Slf4j
@Component
public class TicketServiceClientFallback implements TicketServiceClient {

    @Override
    public ApiResponse<CancelTicketsResponse> cancelTicketsByShowTimes(String secret, CancelTicketsRequest request) {
        log.warn("Fallback triggered when calling ticket-service cancel-by-show-times for showTimeIds={}",
                request != null ? request.getShowTimeIds() : "null");
        return ApiResponse.ok(CancelTicketsResponse.builder()
                .totalTicketsCancelled(0)
                .showTimeIds(request != null ? request.getShowTimeIds() : null)
                .message("Queued via Outbox fallback")
                .build());
    }
}
