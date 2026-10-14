package vn.eventplatform.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.eventplatform.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private UUID id;
    private String orderCode;
    private UUID userId;
    private UUID eventId;
    private UUID showTimeId;
    private UUID promotionId;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private Instant expiresAt;
    private Instant createdAt;
    private List<OrderItemResponse> items;
}
