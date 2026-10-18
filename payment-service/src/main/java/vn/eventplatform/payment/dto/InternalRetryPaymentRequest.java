package vn.eventplatform.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
@Schema(description = "Internal request từ order-service để thử lại thanh toán hoặc đổi cổng thanh toán")
public class InternalRetryPaymentRequest {

    @NotNull(message = "Mã đơn hàng orderId không được để trống")
    private UUID orderId;

    @NotBlank(message = "Mã đơn hàng orderCode không được để trống")
    private String orderCode;

    @NotNull(message = "Tổng tiền thanh toán amount không được để trống")
    @Positive(message = "Tổng tiền thanh toán phải lớn hơn 0")
    private BigDecimal amount;

    @NotNull(message = "Phương thức thanh toán method không được để trống")
    private PaymentGateway method;

    private Instant expiresAt;

    private String customerName;

    private String customerEmail;

    private String clientIp;
}
