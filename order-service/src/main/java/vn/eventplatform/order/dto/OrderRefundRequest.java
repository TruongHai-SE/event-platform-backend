package vn.eventplatform.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu hoàn tiền vé cho đơn hàng")
public class OrderRefundRequest {

    @Schema(description = "Lý do hoàn tiền", example = "Bận việc đột xuất không tham gia được")
    private String reason;
}
