package vn.eventplatform.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.eventplatform.ticket.domain.TicketStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {

    private UUID id;
    private UUID ticketTypeId;
    private UUID showTimeId;
    private UUID orderId;
    private UUID seatId;
    private String qrPayload;
    private String seatNumber;
    private TicketStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private CheckInResponse checkIn;
}
