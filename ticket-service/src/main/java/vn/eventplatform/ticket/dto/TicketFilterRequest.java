package vn.eventplatform.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.eventplatform.ticket.domain.TicketStatus;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketFilterRequest {

    private TicketStatus status;
    private UUID eventId;
    private UUID showTimeId;
}
