package vn.eventplatform.payment.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.eventplatform.payment.domain.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResult {
    private boolean success;
    private UUID refundLogId;
    private String gatewayRefundId;
    private RefundStatus status;
    private BigDecimal refundedAmount;
    private String message;
    private Instant refundedAt;
}
