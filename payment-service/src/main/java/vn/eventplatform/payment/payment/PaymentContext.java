package vn.eventplatform.payment.payment;

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
public class PaymentContext {
    private UUID paymentId;
    private UUID orderId;
    private String orderCode;
    private BigDecimal amount;
    private PaymentGateway method;
    private String clientIp;
    private String returnUrl;
    private String orderInfo;
    private Instant expiresAt;
    private String customerEmail;
    private String customerName;
}
