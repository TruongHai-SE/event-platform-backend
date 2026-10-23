package vn.eventplatform.ticket.repository.projection;

import vn.eventplatform.ticket.domain.TicketStatus;

public interface StatusCountProjection {
    TicketStatus getStatus();
    Long getCount();
}
