package vn.eventplatform.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class CreateOrderItemRequest {

    @NotNull(message = "ticketTypeId không được để trống")
    private UUID ticketTypeId;

    @NotNull(message = "Số lượng vé không được để trống")
    @Min(value = 1, message = "Số lượng vé tối thiểu là 1")
    @Max(value = 10, message = "Số lượng vé tối đa cho mỗi hạng là 10")
    private Integer quantity;

    /**
     * Đơn giá từ client (tùy chọn).
     * Server sẽ luôn kiểm tra và snapshot giá chính thức từ hệ thống sự kiện để bảo mật.
     */
    @DecimalMin(value = "0.0", inclusive = true, message = "Đơn giá vé không được âm")
    private BigDecimal unitPrice;
}
