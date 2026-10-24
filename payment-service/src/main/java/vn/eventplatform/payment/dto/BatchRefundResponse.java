package vn.eventplatform.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả xử lý hoàn tiền hàng loạt")
public class BatchRefundResponse {

    @Schema(description = "Tổng số yêu cầu hoàn tiền gửi vào")
    private int totalRequested;

    @Schema(description = "Số yêu cầu xử lý thành công")
    private int totalSuccess;

    @Schema(description = "Số yêu cầu thất bại")
    private int totalFailed;

    @Schema(description = "Tổng số tiền đã hoàn thành công")
    private BigDecimal totalRefundedAmount;

    @Schema(description = "Kích thước chunk đã áp dụng")
    private int chunkSize;

    @Schema(description = "Tốc độ giới hạn requests/giây đã áp dụng")
    private double requestsPerSecond;

    @Schema(description = "Số lần retry tối đa đã cấu hình")
    private int maxRetries;

    @Schema(description = "Thời gian timeout tối đa cho từng giao dịch (ms)")
    private long itemTimeoutMs;

    @Builder.Default
    @Schema(description = "Chi tiết kết quả từng giao dịch")
    private List<BatchRefundItemResult> results = new ArrayList<>();
}
