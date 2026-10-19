package vn.eventplatform.common.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidEventPayload {
    private UUID orderId;
    private String orderCode;
    private UUID userId;
    private UUID eventId;
    private UUID showTimeId;
    private BigDecimal totalAmount;
    private UUID paymentId;
    private String transactionId;
    private String paymentMethod;
    private Instant paidAt;
    private String customerName;
    private String customerEmail;
    private List<OrderItemEventPayload> items;
}
