package vn.eventplatform.order.client.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherValidationRequest {

    @NotNull(message = "ID người dùng không được để trống")
    private UUID userId;

    private UUID eventId;

    @NotBlank(message = "Mã voucher không được để trống")
    private String code;

    @NotNull(message = "Tổng tiền đơn hàng không được để trống")
    @DecimalMin(value = "0.00", message = "Tổng tiền đơn hàng phải >= 0")
    private BigDecimal orderAmount;
}
