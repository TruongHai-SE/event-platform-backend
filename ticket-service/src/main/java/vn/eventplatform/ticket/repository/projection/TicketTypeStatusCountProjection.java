package vn.eventplatform.ticket.repository.projection;

import vn.eventplatform.ticket.domain.TicketStatus;

import java.util.UUID;

public interface TicketTypeStatusCountProjection {
    UUID getTicketTypeId();
    String getTicketTypeName();
    TicketStatus getStatus();
    Long getCount();
}
