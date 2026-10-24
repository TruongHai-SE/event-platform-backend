package vn.eventplatform.order.client.dto;

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
public class BatchRefundItemResult {
    private UUID refundLogId;
    private UUID paymentId;
    private UUID orderId;
    private BigDecimal amount;
    private String status;
    private String gatewayRefundId;
    private String message;
    private Instant refundedAt;
}
