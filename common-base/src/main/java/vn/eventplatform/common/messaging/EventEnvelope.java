package vn.eventplatform.common.messaging;

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
public class EventEnvelope<T> {
    private UUID eventId;
    private String eventType;
    private Instant occurredAt;
    private String traceId;
    private T payload;

    public static <T> EventEnvelope<T> of(String eventType, T payload, String traceId) {
        return EventEnvelope.<T>builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .occurredAt(Instant.now())
                .traceId(traceId != null && !traceId.isBlank() ? traceId : UUID.randomUUID().toString())
                .payload(payload)
                .build();
    }
}
