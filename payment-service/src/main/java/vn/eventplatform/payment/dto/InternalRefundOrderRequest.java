package vn.eventplatform.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
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
@Schema(description = "Yêu cầu hoàn tiền theo mã đơn hàng từ internal service")
public class InternalRefundOrderRequest {

    @NotNull(message = "Order ID không được để trống")
    @Schema(description = "ID của đơn hàng cần hoàn tiền")
    private UUID orderId;

    @NotNull(message = "Số tiền hoàn không được để trống")
    @DecimalMin(value = "0.00", message = "Số tiền hoàn không được âm")
    @Schema(description = "Số tiền muốn hoàn", example = "100000.00")
    private BigDecimal amount;

    @Schema(description = "Lý do hoàn tiền", example = "Khách hàng yêu cầu hủy đơn hoặc sự kiện bị hủy")
    private String reason;

    @Schema(description = "Khóa idempotent để chống duplicate request hoàn tiền", example = "REFUND-ORD-20261018-001")
    private String idempotencyKey;
}
