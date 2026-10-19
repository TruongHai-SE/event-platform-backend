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
public class OrderExpiredEventPayload {
    private UUID orderId;
    private String orderCode;
    private UUID userId;
    private UUID eventId;
    private UUID showTimeId;
    private Instant expiredAt;
    private UUID promotionId;
    private List<OrderItemEventPayload> releasedItems;
}
