package vn.eventplatform.payment.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.eventplatform.payment.domain.PaymentGateway;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("CreatePaymentRequest - Valid request with orderId passes validation")
    void createPaymentRequest_Valid_Passes() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(UUID.randomUUID())
                .method(PaymentGateway.VNPAY)
                .build();

        Set<ConstraintViolation<CreatePaymentRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("CreatePaymentRequest - Valid request with orderCode passes validation")
    void createPaymentRequest_WithOrderCode_Valid_Passes() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderCode("TKZ-20261019-001")
                .method(PaymentGateway.SEPAY)
                .build();

        Set<ConstraintViolation<CreatePaymentRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("CreatePaymentRequest - Null orderId and orderCode fails validation")
    void createPaymentRequest_NullFields_Fails() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(null)
                .orderCode(null)
                .method(null)
                .build();

        Set<ConstraintViolation<CreatePaymentRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(2);
    }

    @Test
    @DisplayName("CreateRefundRequest - Valid request passes validation")
    void createRefundRequest_Valid_Passes() {
        CreateRefundRequest request = CreateRefundRequest.builder()
                .paymentId(UUID.randomUUID())
                .amount(new BigDecimal("50000.00"))
                .reason("Event cancelled")
                .idempotencyKey("IDEM-12345")
                .build();

        Set<ConstraintViolation<CreateRefundRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("CreateRefundRequest - Negative or zero amount fails validation")
    void createRefundRequest_InvalidAmount_Fails() {
        CreateRefundRequest request = CreateRefundRequest.builder()
                .paymentId(UUID.randomUUID())
                .amount(new BigDecimal("0.50"))
                .reason("Event cancelled")
                .build();

        Set<ConstraintViolation<CreateRefundRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
    }
}
