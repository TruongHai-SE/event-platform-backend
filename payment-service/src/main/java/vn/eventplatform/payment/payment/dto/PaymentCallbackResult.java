package vn.eventplatform.payment.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.eventplatform.payment.domain.PaymentGateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCallbackResult {
    private boolean success;
    private PaymentGateway method;
    private String transactionId;
    private String orderCode;
    private UUID orderId;
    private BigDecimal amount;
    private String responseCode;
    private String message;
    private Instant payDate;
}
