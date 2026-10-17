package vn.eventplatform.order.client.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherApplyRequest {

    @NotNull(message = "Order ID không được để trống")
    private UUID orderId;

    @NotNull(message = "User ID không được để trống")
    private UUID userId;

    private UUID eventId;

    @NotBlank(message = "Mã voucher không được để trống")
    private String code;

    @NotNull(message = "Tổng tiền đơn hàng không được để trống")
    @DecimalMin(value = "0.00", message = "Tổng tiền đơn hàng không được âm")
    private BigDecimal orderAmount;
}
