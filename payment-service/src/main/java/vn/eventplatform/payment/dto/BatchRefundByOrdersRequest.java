package vn.eventplatform.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu hoàn tiền hàng loạt theo orderId (dành cho IPC nội bộ)")
public class BatchRefundByOrdersRequest {

    @NotEmpty(message = "Danh sách đơn hàng cần hoàn tiền không được để trống")
    @Schema(description = "Danh sách yêu cầu hoàn tiền theo đơn hàng")
    private List<@Valid InternalRefundOrderRequest> requests;

    @Schema(description = "Kích thước mỗi chunk xử lý (mặc định lấy theo cấu hình hệ thống)", example = "50")
    private Integer chunkSize;

    @Schema(description = "Tốc độ giới hạn request/giây (mặc định lấy theo cấu hình hệ thống)", example = "10.0")
    private Double requestsPerSecond;
}
