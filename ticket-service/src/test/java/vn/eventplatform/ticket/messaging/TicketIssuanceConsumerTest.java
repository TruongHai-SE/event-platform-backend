package vn.eventplatform.ticket.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.eventplatform.common.messaging.EventEnvelope;
import vn.eventplatform.common.messaging.EventPlatformMessagingConfig;
import vn.eventplatform.common.messaging.EventPlatformMessagingConfig.IdempotentConsumerTemplate;
import vn.eventplatform.common.messaging.EventPlatformMessagingConfig.NonRetryableEventException;
import vn.eventplatform.common.messaging.event.OrderItemEventPayload;
import vn.eventplatform.common.messaging.event.OrderPaidEventPayload;
import vn.eventplatform.common.messaging.event.PaymentSuccessEventPayload;
import vn.eventplatform.ticket.service.TicketService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketIssuanceConsumerTest {

    @Mock
    private TicketService ticketService;

    private ObjectMapper objectMapper;
    private TicketIssuanceConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new TicketIssuanceConsumer(ticketService, objectMapper);
    }

    @Test
    @DisplayName("1. Xử lý thành công event payment.success: giải mã payload và gọi ticketService.issueTicketsForOrder")
    void handlePaymentSuccessEvent_Success() {
        UUID orderId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId1 = UUID.randomUUID();
        UUID ticketTypeId2 = UUID.randomUUID();
        String orderCode = "TKZ-20261020-00001";

        List<OrderItemEventPayload> items = List.of(
                OrderItemEventPayload.builder()
                        .ticketTypeId(ticketTypeId1)
                        .ticketTypeName("Vé VIP")
                        .quantity(2)
                        .unitPrice(new BigDecimal("500000"))
                        .subtotal(new BigDecimal("1000000"))
                        .build(),
                OrderItemEventPayload.builder()
                        .ticketTypeId(ticketTypeId2)
                        .ticketTypeName("Vé Thường")
                        .quantity(1)
                        .unitPrice(new BigDecimal("200000"))
                        .subtotal(new BigDecimal("200000"))
                        .build()
        );

        PaymentSuccessEventPayload payload = PaymentSuccessEventPayload.builder()
                .paymentId(UUID.randomUUID())
                .orderId(orderId)
                .orderCode(orderCode)
                .userId(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .showTimeId(showTimeId)
                .amount(new BigDecimal("1200000"))
                .paymentMethod("VNPAY")
                .transactionId("VNP12345678")
                .paidAt(Instant.now())
                .items(items)
                .build();

        EventEnvelope<PaymentSuccessEventPayload> envelope = EventEnvelope.<PaymentSuccessEventPayload>builder()
                .eventId(UUID.randomUUID())
                .eventType(EventPlatformMessagingConfig.EVENT_TYPE_PAYMENT_SUCCESS)
                .occurredAt(Instant.now())
                .traceId("trace-test-01")
                .payload(payload)
                .build();

        consumer.handleTicketReservationMessage(envelope);

        verify(ticketService, times(1)).issueTicketsForOrder(
                eq(orderId),
                eq(payload.getEventId()),
                eq(showTimeId),
                eq(orderCode),
                argThat(list -> list.size() == 2 && list.get(0).getQuantity() == 2 && list.get(1).getQuantity() == 1),
                eq(payload.getUserId())
        );
    }

    @Test
    @DisplayName("2. Xử lý thành công event order.paid từ order-service")
    void handleOrderPaidEvent_Success() {
        UUID orderId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        String orderCode = "TKZ-20261020-00002";

        OrderPaidEventPayload payload = OrderPaidEventPayload.builder()
                .orderId(orderId)
                .orderCode(orderCode)
                .userId(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .showTimeId(showTimeId)
                .totalAmount(new BigDecimal("300000"))
                .paymentMethod("SEPAY")
                .items(List.of(
                        OrderItemEventPayload.builder()
                                .ticketTypeId(ticketTypeId)
                                .ticketTypeName("Vé Phổ Thông")
                                .quantity(1)
                                .unitPrice(new BigDecimal("300000"))
                                .subtotal(new BigDecimal("300000"))
                                .build()
                ))
                .build();

        EventEnvelope<OrderPaidEventPayload> envelope = EventEnvelope.<OrderPaidEventPayload>builder()
                .eventId(UUID.randomUUID())
                .eventType(EventPlatformMessagingConfig.EVENT_TYPE_ORDER_PAID)
                .occurredAt(Instant.now())
                .traceId("trace-test-02")
                .payload(payload)
                .build();

        consumer.handleTicketReservationMessage(envelope);

        verify(ticketService, times(1)).issueTicketsForOrder(
                eq(orderId),
                eq(payload.getEventId()),
                eq(showTimeId),
                eq(orderCode),
                any(),
                eq(payload.getUserId())
        );
    }

    @Test
    @DisplayName("3. Tự động chuyển đổi payload dạng Map/LinkedHashMap sang PaymentSuccessEventPayload mà không bị ClassCastException")
    void handleMessage_WithMapPayload_DeserializesCorrectly() {
        UUID orderId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        String orderCode = "TKZ-20261020-00003";

        Map<String, Object> itemMap = new HashMap<>();
        itemMap.put("ticketTypeId", ticketTypeId.toString());
        itemMap.put("ticketTypeName", "Vé Demo");
        itemMap.put("quantity", 3);
        itemMap.put("unitPrice", 150000);

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("orderId", orderId.toString());
        payloadMap.put("orderCode", orderCode);
        payloadMap.put("showTimeId", showTimeId.toString());
        payloadMap.put("amount", 450000);
        payloadMap.put("items", List.of(itemMap));

        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.<Map<String, Object>>builder()
                .eventId(UUID.randomUUID())
                .eventType(EventPlatformMessagingConfig.EVENT_TYPE_PAYMENT_SUCCESS)
                .occurredAt(Instant.now())
                .traceId("trace-map-test")
                .payload(payloadMap)
                .build();

        consumer.handleTicketReservationMessage(envelope);

        verify(ticketService, times(1)).issueTicketsForOrder(
                eq(orderId),
                any(),
                eq(showTimeId),
                eq(orderCode),
                argThat(list -> list.size() == 1 && list.get(0).getQuantity() == 3),
                any()
        );
    }

    @Test
    @DisplayName("4. Bỏ qua an toàn khi gặp eventType không liên quan (không ném ngoại lệ)")
    void handleMessage_UnknownEventType_IgnoredSafely() {
        EventEnvelope<String> envelope = EventEnvelope.<String>builder()
                .eventId(UUID.randomUUID())
                .eventType("order.cancelled")
                .occurredAt(Instant.now())
                .traceId("trace-ignored")
                .payload("dummy-payload")
                .build();

        consumer.handleTicketReservationMessage(envelope);

        verify(ticketService, never()).issueTicketsForOrder(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("5. Tích hợp IdempotentConsumerTemplate: chống xử lý lặp lại cho cùng 1 eventId")
    void handleMessage_WithIdempotentConsumerTemplate_Deduplicates() {
        IdempotentConsumerTemplate template = new IdempotentConsumerTemplate(null);

        // Inject consumerTemplate qua setter hoặc field reflection
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "consumerTemplate", template);

        UUID orderId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        PaymentSuccessEventPayload payload = PaymentSuccessEventPayload.builder()
                .orderId(orderId)
                .showTimeId(showTimeId)
                .orderCode("TKZ-DEDUP-001")
                .items(List.of(
                        OrderItemEventPayload.builder()
                                .ticketTypeId(UUID.randomUUID())
                                .quantity(1)
                                .build()
                ))
                .build();

        EventEnvelope<PaymentSuccessEventPayload> envelope = EventEnvelope.<PaymentSuccessEventPayload>builder()
                .eventId(eventId)
                .eventType(EventPlatformMessagingConfig.EVENT_TYPE_PAYMENT_SUCCESS)
                .occurredAt(Instant.now())
                .traceId("trace-dedup")
                .payload(payload)
                .build();

        // Gửi lần 1: xử lý thành công
        consumer.handleTicketReservationMessage(envelope);

        // Gửi lần 2 cùng eventId: bị chặn bởi IdempotentConsumerTemplate
        consumer.handleTicketReservationMessage(envelope);

        // Đảm bảo ticketService chỉ được gọi đúng 1 lần duy nhất
        verify(ticketService, times(1)).issueTicketsForOrder(eq(orderId), any(), eq(showTimeId), any(), any(), any());
    }

    @Test
    @DisplayName("6. Xử lý payload hỏng: ném NonRetryableEventException để đẩy thẳng DLQ")
    void handleMessage_PoisonPayload_ThrowsNonRetryableEventException() {
        EventEnvelope<String> poisonEnvelope = EventEnvelope.<String>builder()
                .eventId(UUID.randomUUID())
                .eventType(EventPlatformMessagingConfig.EVENT_TYPE_PAYMENT_SUCCESS)
                .occurredAt(Instant.now())
                .traceId("trace-poison")
                .payload("{corrupted-json-cannot-parse-as-payload}")
                .build();

        assertThatThrownBy(() -> consumer.handleTicketReservationMessage(poisonEnvelope))
                .isInstanceOf(NonRetryableEventException.class);

        verify(ticketService, never()).issueTicketsForOrder(any(), any(), any(), any(), any(), any());
    }
}
