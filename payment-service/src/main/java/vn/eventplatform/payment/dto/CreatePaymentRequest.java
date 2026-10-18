package vn.eventplatform.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.eventplatform.payment.domain.PaymentGateway;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request khởi tạo thanh toán cho đơn hàng")
public class CreatePaymentRequest {

    @NotNull(message = "Mã đơn hàng orderId không được để trống")
    @Schema(description = "ID của đơn hàng", example = "a0000000-0000-0000-0000-000000000001")
    private UUID orderId;

    @NotNull(message = "Phương thức thanh toán method không được để trống")
    @Schema(description = "Cổng thanh toán lựa chọn: VNPAY, MOMO, SEPAY", example = "VNPAY")
    private PaymentGateway method;
}
