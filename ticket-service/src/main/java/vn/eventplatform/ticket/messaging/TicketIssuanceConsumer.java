package vn.eventplatform.ticket.messaging;

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
import vn.eventplatform.common.messaging.EventPlatformMessagingConfig.NonRetryableEventException;
import vn.eventplatform.common.messaging.event.OrderPaidEventPayload;
import vn.eventplatform.common.messaging.event.PaymentSuccessEventPayload;
import vn.eventplatform.ticket.service.TicketService;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
public class TicketIssuanceConsumer {

    private final TicketService ticketService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private IdempotentConsumerTemplate consumerTemplate;

    @RabbitListener(queues = EventPlatformMessagingConfig.QUEUE_TICKET_RESERVATION)
    public void handleTicketReservationMessage(EventEnvelope<?> envelope) {
        if (envelope == null) {
            log.warn("Nhận thông điệp rỗng trên hàng đợi {}", EventPlatformMessagingConfig.QUEUE_TICKET_RESERVATION);
            return;
        }

        String eventType = envelope.getEventType();
        log.info("Nhận sự kiện trên hàng đợi {}: eventType={}, eventId={}",
                EventPlatformMessagingConfig.QUEUE_TICKET_RESERVATION, eventType, envelope.getEventId());

        if (consumerTemplate != null) {
            consumerTemplate.executeWithRetryAndDlq("ticket-service", envelope, 3, this::dispatchMessage);
        } else {
            dispatchMessage(envelope);
        }
    }

    private void dispatchMessage(EventEnvelope<?> envelope) {
        String eventType = envelope.getEventType();
        if (EventPlatformMessagingConfig.EVENT_TYPE_PAYMENT_SUCCESS.equalsIgnoreCase(eventType)) {
            PaymentSuccessEventPayload payload = extractPayload(envelope.getPayload(), PaymentSuccessEventPayload.class);
            if (payload != null) {
                log.info("Bắt đầu xử lý phát hành vé tự động từ payment.success: orderId={}, orderCode={}",
                        payload.getOrderId(), payload.getOrderCode());
                ticketService.issueTicketsForOrder(
                        payload.getOrderId(),
                        payload.getEventId(),
                        payload.getShowTimeId(),
                        payload.getOrderCode(),
                        payload.getItems(),
                        payload.getUserId()
                );
            }
        } else if (EventPlatformMessagingConfig.EVENT_TYPE_ORDER_PAID.equalsIgnoreCase(eventType)) {
            OrderPaidEventPayload payload = extractPayload(envelope.getPayload(), OrderPaidEventPayload.class);
            if (payload != null) {
                log.info("Bắt đầu xử lý phát hành vé tự động từ order.paid: orderId={}, orderCode={}",
                        payload.getOrderId(), payload.getOrderCode());
                ticketService.issueTicketsForOrder(
                        payload.getOrderId(),
                        payload.getEventId(),
                        payload.getShowTimeId(),
                        payload.getOrderCode(),
                        payload.getItems(),
                        payload.getUserId()
                );
            }
        } else {
            log.info("Bỏ qua sự kiện không thuộc luồng phát hành vé: eventType={}", eventType);
        }
    }

    private <T> T extractPayload(Object rawPayload, Class<T> targetClass) {
        if (rawPayload == null) {
            return null;
        }
        if (targetClass.isInstance(rawPayload)) {
            return targetClass.cast(rawPayload);
        }
        try {
            return objectMapper.convertValue(rawPayload, targetClass);
        } catch (Exception e) {
            log.error("Không thể chuyển đổi payload sang {}: {}", targetClass.getSimpleName(), e.getMessage());
            throw new NonRetryableEventException("Không thể giải mã payload cho " + targetClass.getSimpleName(), e);
        }
    }
}
