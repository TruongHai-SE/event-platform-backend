package vn.eventplatform.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Yêu cầu xác nhận hoàn tiền thủ công qua ngân hàng")
public class ConfirmManualRefundRequest {

    @NotBlank(message = "Mã tham chiếu ngân hàng/chứng từ chuyển tiền không được để trống")
    @Schema(description = "Mã giao dịch ngân hàng hoặc mã ủy nhiệm chi (FT/Reference No)", example = "FT26102399998888")
    private String gatewayRefundId;

    @Schema(description = "Ghi chú của kế toán/admin", example = "Đã chuyển trả khách qua MB Bank lúc 15:30")
    private String note;
}
