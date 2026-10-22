package vn.eventplatform.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQrPayloadDto {
    private UUID ticketId;
    private UUID eventId;
    private UUID showTimeId;
    private UUID ticketTypeId;
    private String ticketTypeName;
    private UUID orderId;
    private String seatNumber;
    private Instant issuedAt;
}
