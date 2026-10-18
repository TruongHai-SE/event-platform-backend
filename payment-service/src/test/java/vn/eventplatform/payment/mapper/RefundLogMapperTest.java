package vn.eventplatform.payment.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vn.eventplatform.payment.domain.Payment;
import vn.eventplatform.payment.domain.RefundLog;
import vn.eventplatform.payment.domain.RefundStatus;
import vn.eventplatform.payment.dto.RefundResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefundLogMapperTest {

    private final RefundLogMapper mapper = Mappers.getMapper(RefundLogMapper.class);

    @Test
    @DisplayName("toResponse maps RefundLog and extracts paymentId")
    void testToResponse() {
        Payment payment = Payment.builder().id(UUID.randomUUID()).build();
        RefundLog refundLog = RefundLog.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .payment(payment)
                .amount(new BigDecimal("50000.00"))
                .reason("Customer requested refund")
                .status(RefundStatus.SUCCESS)
                .gatewayRefundId("GW-REF-001")
                .refundedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        RefundResponse response = mapper.toResponse(refundLog);

        assertThat(response.getId()).isEqualTo(refundLog.getId());
        assertThat(response.getPaymentId()).isEqualTo(payment.getId());
        assertThat(response.getOrderId()).isEqualTo(refundLog.getOrderId());
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(response.getStatus()).isEqualTo(RefundStatus.SUCCESS);
    }
}
