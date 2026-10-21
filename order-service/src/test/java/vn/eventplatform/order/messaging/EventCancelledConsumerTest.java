package vn.eventplatform.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.eventplatform.common.messaging.EventEnvelope;
import vn.eventplatform.common.messaging.EventPlatformMessagingConfig;
import vn.eventplatform.common.messaging.event.EventCancelledEventPayload;
import vn.eventplatform.order.service.OrderService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventCancelledConsumerTest {

    @Mock
    private OrderService orderService;

    private ObjectMapper objectMapper;
    private EventCancelledConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new EventCancelledConsumer(orderService, objectMapper);
    }

    @Test
    @DisplayName("Bỏ qua message nếu envelope là null")
    void shouldIgnoreNullEnvelope() {
        consumer.handleEventCancelled(null);
        verify(orderService, never()).processEventCancellation(any());
    }

    @Test
    @DisplayName("Bỏ qua message nếu eventType không khớp")
    void shouldIgnoreMismatchedEventType() {
        EventEnvelope<String> envelope = EventEnvelope.<String>builder()
                .eventId(UUID.randomUUID())
                .eventType("order.created")
                .payload("test")
                .build();

        consumer.handleEventCancelled(envelope);
        verify(orderService, never()).processEventCancellation(any());
    }

    @Test
    @DisplayName("Chuyển đổi payload và gọi processEventCancellation thành công")
    void shouldProcessEventCancelledSuccessfully() {
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();

        EventCancelledEventPayload payload = EventCancelledEventPayload.builder()
                .eventId(eventId)
                .showTimeIds(List.of(showTimeId))
                .cancellationReason("Lý do bất khả kháng")
                .cancelledAt(Instant.now())
                .build();

        EventEnvelope<EventCancelledEventPayload> envelope = EventEnvelope.<EventCancelledEventPayload>builder()
                .eventId(UUID.randomUUID())
                .eventType(EventPlatformMessagingConfig.EVENT_TYPE_EVENT_CANCELLED)
                .payload(payload)
                .build();

        consumer.handleEventCancelled(envelope);

        verify(orderService, times(1)).processEventCancellation(argThat(p ->
                p.getEventId().equals(eventId) &&
                "Lý do bất khả kháng".equals(p.getCancellationReason())
        ));
    }
}
