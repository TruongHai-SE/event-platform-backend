package vn.eventplatform.common.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEventPayload {
    private UUID paymentId;
    private UUID orderId;
    private String orderCode;
    private BigDecimal amount;
    private String paymentMethod;
    private String transactionId;
    private String failureReason;
    private Instant failedAt;
}
