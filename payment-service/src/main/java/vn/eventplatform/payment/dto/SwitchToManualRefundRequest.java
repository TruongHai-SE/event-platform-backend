package vn.eventplatform.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Yêu cầu chuyển trạng thái hoàn tiền sang MANUAL_BANK")
public class SwitchToManualRefundRequest {

    @Schema(description = "Lý do chuyển sang hoàn tiền thủ công qua ngân hàng", example = "Cổng thanh toán báo lỗi, chuyển cho kế toán xử lý")
    private String note;
}
