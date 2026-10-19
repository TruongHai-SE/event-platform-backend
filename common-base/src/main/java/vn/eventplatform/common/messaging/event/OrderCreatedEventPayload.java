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
public class OrderCreatedEventPayload {
    private UUID orderId;
    private String orderCode;
    private UUID userId;
    private UUID eventId;
    private UUID showTimeId;
    private UUID promotionId;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String status;
    private Instant expiresAt;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private List<OrderItemEventPayload> items;
}
