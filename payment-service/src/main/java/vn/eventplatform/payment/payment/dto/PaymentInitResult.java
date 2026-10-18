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
public class PaymentInitResult {
    private UUID paymentId;
    private UUID orderId;
    private PaymentGateway method;
    private BigDecimal amount;
    private String paymentUrl;
    private String qrCode;
}
