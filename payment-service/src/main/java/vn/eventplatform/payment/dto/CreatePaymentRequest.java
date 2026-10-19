package vn.eventplatform.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
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

    @Schema(description = "Mã đơn hàng (VD: TKZ-20261019-XXXXX)", example = "TKZ-20261018-001")
    private String orderCode;

    @Schema(description = "ID của đơn hàng (tuỳ chọn nếu đã cung cấp orderCode)", example = "a0000000-0000-0000-0000-000000000001")
    private UUID orderId;

    @NotNull(message = "Phương thức thanh toán method không được để trống")
    @Schema(description = "Cổng thanh toán lựa chọn: VNPAY, MOMO, SEPAY", example = "VNPAY")
    private PaymentGateway method;

    public CreatePaymentRequest(String orderCode, PaymentGateway method) {
        this.orderCode = orderCode;
        this.method = method;
    }

    public CreatePaymentRequest(UUID orderId, PaymentGateway method) {
        this.orderId = orderId;
        this.method = method;
    }

    @AssertTrue(message = "Mã đơn hàng (orderCode) hoặc ID đơn hàng (orderId) không được để trống")
    @JsonIgnore
    public boolean isHasOrderIdentifier() {
        return (orderCode != null && !orderCode.trim().isEmpty()) || orderId != null;
    }
}
