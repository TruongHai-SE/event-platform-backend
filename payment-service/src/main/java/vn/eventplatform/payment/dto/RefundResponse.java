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
@Schema(description = "Thông tin kết quả hoàn tiền")
public class RefundResponse {

    @Schema(description = "ID bản ghi nhật ký hoàn tiền")
    private UUID id;

    @Schema(description = "ID đơn hàng")
    private UUID orderId;

    @Schema(description = "ID giao dịch thanh toán gốc")
    private UUID paymentId;

    @Schema(description = "Số tiền đã hoàn")
    private BigDecimal amount;

    @Schema(description = "Lý do hoàn tiền")
    private String reason;

    @Schema(description = "Trạng thái hoàn tiền (PROCESSING, SUCCESS, FAILED, MANUAL_BANK)")
    private RefundStatus status;

    @Schema(description = "Mã hoàn tiền từ cổng thanh toán")
    private String gatewayRefundId;

    @Schema(description = "Thời điểm hoàn tất hoàn tiền")
    private Instant refundedAt;

    @Schema(description = "Thời điểm tạo yêu cầu")
    private Instant createdAt;
}
