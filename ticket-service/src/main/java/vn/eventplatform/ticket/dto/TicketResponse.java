package vn.eventplatform.ticket.dto;

import lombok.*;
import vn.eventplatform.ticket.domain.TicketStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {

    private UUID id;
    private UUID userId;
    private UUID eventId;
    private UUID ticketTypeId;
    private String ticketTypeName;
    private UUID showTimeId;
    private UUID orderId;
    private UUID seatId;
    private String qrPayload;
    private String qrImageUrl;
    private String seatNumber;
    private TicketStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private CheckInResponse checkIn;
}
