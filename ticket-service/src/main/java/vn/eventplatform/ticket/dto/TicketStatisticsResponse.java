package vn.eventplatform.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.eventplatform.ticket.domain.CheckInMethod;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketStatisticsResponse {

    private UUID eventId;
    private UUID showTimeId;
    private long totalIssued;
    private long totalSold;
    private long totalCheckedIn;
    private long totalCancelled;
    private double checkInRate;
    private Instant lastCheckedInAt;
    private Map<CheckInMethod, Long> checkInByMethod;
    private List<TicketTypeStatisticsResponse> byTicketType;

    @Getter
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketTypeStatisticsResponse {
        private UUID ticketTypeId;
        private String ticketTypeName;
        private long totalIssued;
        private long totalSold;
        private long totalCheckedIn;
        private long totalCancelled;
        private double checkInRate;
    }
}
