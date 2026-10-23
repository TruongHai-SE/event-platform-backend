package vn.eventplatform.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.eventplatform.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả hoàn vé và hoàn tiền đơn hàng")
public class OrderRefundResponse {

    @Schema(description = "ID của đơn hàng")
    private UUID orderId;

    @Schema(description = "Mã đơn hàng", example = "ORD-20261021-0001")
    private String orderCode;

    @Schema(description = "Trạng thái đơn hàng sau khi hoàn tiền")
    private OrderStatus status;

    @Schema(description = "Tổng tiền thanh toán gốc của đơn hàng")
    private BigDecimal originalAmount;

    @Schema(description = "Tỷ lệ phí khấu trừ hoàn vé (%)", example = "10.00")
    private BigDecimal refundFeePercent;

    @Schema(description = "Số tiền phí khấu trừ", example = "50000.00")
    private BigDecimal refundFeeAmount;

    @Schema(description = "Số tiền thực tế được hoàn lại cho khách hàng", example = "450000.00")
    private BigDecimal refundAmount;

    @Schema(description = "Lý do hoàn tiền")
    private String reason;

    @Schema(description = "Thời gian xử lý hoàn tiền")
    private Instant refundedAt;

    @Schema(description = "Thông báo kết quả")
    private String message;
}
