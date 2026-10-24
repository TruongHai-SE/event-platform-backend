package vn.eventplatform.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.eventplatform.payment.domain.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả hoàn tiền của từng giao dịch trong lô")
public class BatchRefundItemResult {

    @Schema(description = "ID bản ghi RefundLog (nếu đã tạo)")
    private UUID refundLogId;

    @Schema(description = "ID của giao dịch thanh toán gốc")
    private UUID paymentId;

    @Schema(description = "ID của đơn hàng gốc")
    private UUID orderId;

    @Schema(description = "Số tiền hoàn")
    private BigDecimal amount;

    @Schema(description = "Trạng thái hoàn tiền")
    private RefundStatus status;

    @Schema(description = "Mã tham chiếu từ cổng thanh toán / ngân hàng")
    private String gatewayRefundId;

    @Schema(description = "Thông báo kết quả hoặc nguyên nhân lỗi")
    private String message;

    @Schema(description = "Thời điểm hoàn tiền thành công")
    private Instant refundedAt;

    @Schema(description = "Số lần đã thử lại khi gặp lỗi tạm thời")
    private int retryAttempts;
}
