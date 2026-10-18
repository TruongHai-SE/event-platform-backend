package vn.eventplatform.payment.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.eventplatform.payment.domain.PaymentGateway;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundContext {
    private UUID refundLogId;
    private UUID orderId;
    private String orderCode;
    private String originalTransactionId;
    private BigDecimal amount;
    private String reason;
    private String idempotencyKey;
    private PaymentGateway method;
}
