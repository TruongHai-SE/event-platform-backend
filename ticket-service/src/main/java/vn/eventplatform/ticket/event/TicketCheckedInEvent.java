package vn.eventplatform.ticket.event;

import java.time.Instant;
import java.util.UUID;

public record TicketCheckedInEvent(
        UUID eventId,
        UUID showTimeId,
        UUID ticketId,
        UUID staffId,
        Instant checkedInAt
) {}
