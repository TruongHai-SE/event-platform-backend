package vn.eventplatform.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.eventplatform.order.domain.PaymentGatewayType;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request thử lại thanh toán hoặc đổi cổng thanh toán cho đơn hàng")
public class RetryPaymentRequest {

    @NotNull(message = "Phương thức thanh toán (method) không được để trống")
    @Schema(description = "Cổng thanh toán lựa chọn: VNPAY, MOMO, SEPAY", example = "SEPAY")
    private PaymentGatewayType method;
}
