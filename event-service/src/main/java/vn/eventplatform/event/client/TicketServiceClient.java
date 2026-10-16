package vn.eventplatform.event.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import vn.eventplatform.common.dto.ApiResponse;

import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "ticket-service",
        url = "${services.ticket-service.url:http://localhost:8083}",
        fallback = TicketServiceClientFallback.class
)
public interface TicketServiceClient {

    @PostMapping("/internal/v1/tickets/cancel-by-show-times")
    ApiResponse<CancelTicketsResponse> cancelTicketsByShowTimes(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody CancelTicketsRequest request
    );

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class CancelTicketsRequest {
        private List<UUID> showTimeIds;
        private String cancellationReason;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class CancelTicketsResponse {
        private int totalTicketsCancelled;
        private List<UUID> showTimeIds;
        private String message;
    }
}
