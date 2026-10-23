package vn.eventplatform.common.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCancelledEventPayload {
    private UUID eventId;
    private List<UUID> showTimeIds;
    private String cancellationReason;
    private Instant cancelledAt;
}
