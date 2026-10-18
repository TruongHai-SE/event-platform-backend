package vn.eventplatform.payment.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vn.eventplatform.payment.domain.Payment;
import vn.eventplatform.payment.domain.PaymentGateway;
import vn.eventplatform.payment.domain.PaymentStatus;
import vn.eventplatform.payment.dto.PaymentInitResponse;
import vn.eventplatform.payment.dto.PaymentResponse;
import vn.eventplatform.payment.payment.dto.PaymentInitResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMapperTest {

    private final PaymentMapper paymentMapper = Mappers.getMapper(PaymentMapper.class);

    @Test
    @DisplayName("toResponse maps all fields correctly")
    void testToResponse() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();

        Payment payment = Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .orderCode("ORD-123")
                .method(PaymentGateway.VNPAY)
                .transactionId("TRANS-999")
                .amount(new BigDecimal("250000.00"))
                .status(PaymentStatus.SUCCESS)
                .paidAt(now)
                .createdAt(now)
                .build();

        PaymentResponse response = paymentMapper.toResponse(payment);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(paymentId);
        assertThat(response.getOrderId()).isEqualTo(orderId);
        assertThat(response.getOrderCode()).isEqualTo("ORD-123");
        assertThat(response.getMethod()).isEqualTo(PaymentGateway.VNPAY);
        assertThat(response.getTransactionId()).isEqualTo("TRANS-999");
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("250000.00"));
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("toInitResponse combines Payment and PaymentInitResult")
    void testToInitResponse() {
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .orderCode("ORD-123")
                .method(PaymentGateway.VNPAY)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.PENDING)
                .build();

        PaymentInitResult initResult = PaymentInitResult.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .method(PaymentGateway.VNPAY)
                .paymentUrl("https://sandbox.vnpayment.vn/pay")
                .qrCode("qr-content")
                .build();

        PaymentInitResponse response = paymentMapper.toInitResponse(payment, initResult);

        assertThat(response.getPaymentId()).isEqualTo(payment.getId());
        assertThat(response.getOrderCode()).isEqualTo("ORD-123");
        assertThat(response.getPaymentUrl()).isEqualTo("https://sandbox.vnpayment.vn/pay");
        assertThat(response.getQrCode()).isEqualTo("qr-content");
    }
}
