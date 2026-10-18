package vn.eventplatform.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.eventplatform.order.domain.PaymentGatewayType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalRetryPaymentRequest {

    private UUID orderId;
    private String orderCode;
    private BigDecimal amount;
    private PaymentGatewayType method;
    private Instant expiresAt;
    private String customerName;
    private String customerEmail;
    private String clientIp;
}
