package vn.eventplatform.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.eventplatform.common.messaging.EventEnvelope;
import vn.eventplatform.common.messaging.EventPlatformMessagingConfig;
import vn.eventplatform.common.messaging.EventPlatformMessagingConfig.IdempotentConsumerTemplate;
import vn.eventplatform.common.messaging.event.EventCancelledEventPayload;
import vn.eventplatform.order.service.OrderService;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EventCancelledConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private IdempotentConsumerTemplate idempotentConsumerTemplate;

    @RabbitListener(queues = EventPlatformMessagingConfig.QUEUE_ORDER_EVENT_CANCELLED)
    public void handleEventCancelled(EventEnvelope<?> envelope) {
        if (envelope == null) {
            log.warn("Nhận thông điệp rỗng trên hàng đợi {}", EventPlatformMessagingConfig.QUEUE_ORDER_EVENT_CANCELLED);
            return;
        }

        if (idempotentConsumerTemplate != null) {
            idempotentConsumerTemplate.executeWithRetryAndDlq("order-service", envelope, 3, this::dispatchMessage);
        } else {
            dispatchMessage(envelope);
        }
    }

    private void dispatchMessage(EventEnvelope<?> envelope) {
        String eventType = envelope.getEventType();
        if (!EventPlatformMessagingConfig.EVENT_TYPE_EVENT_CANCELLED.equalsIgnoreCase(eventType)) {
            log.info("Bỏ qua sự kiện không khớp trong EventCancelledConsumer: eventType={}", eventType);
            return;
        }

        try {
            EventCancelledEventPayload payload = objectMapper.convertValue(
                    envelope.getPayload(),
                    EventCancelledEventPayload.class
            );
            log.info("Nhận sự kiện event.cancelled: eventId={}, reason={}. Bắt đầu xử lý hoàn tiền tự động",
                    payload.getEventId(), payload.getCancellationReason());
            orderService.processEventCancellation(payload);
        } catch (Exception e) {
            log.error("Lỗi khi xử lý sự kiện event.cancelled: {}", envelope, e);
            throw new EventPlatformMessagingConfig.NonRetryableEventException("Không thể xử lý sự kiện event.cancelled", e);
        }
    }
}
