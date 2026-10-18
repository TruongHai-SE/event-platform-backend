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
@Schema(description = "Yêu cầu hoàn tiền cho giao dịch thanh toán")
public class CreateRefundRequest {

    @NotNull(message = "Payment ID không được để trống")
    @Schema(description = "ID của giao dịch thanh toán gốc cần hoàn tiền")
    private UUID paymentId;

    @NotNull(message = "Số tiền hoàn không được để trống")
    @DecimalMin(value = "1.00", message = "Số tiền hoàn tối thiểu là 1 VNĐ")
    @Schema(description = "Số tiền muốn hoàn", example = "100000.00")
    private BigDecimal amount;

    @Schema(description = "Lý do hoàn tiền", example = "Khách hàng yêu cầu hủy vé sự kiện")
    private String reason;

    @Schema(description = "Khóa idempotent để chống duplicate request hoàn tiền", example = "REFUND-ORD-20261018-001")
    private String idempotencyKey;
}
