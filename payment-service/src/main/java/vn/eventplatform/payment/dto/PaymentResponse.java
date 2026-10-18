package vn.eventplatform.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.eventplatform.payment.domain.PaymentGateway;
import vn.eventplatform.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết giao dịch thanh toán")
public class PaymentResponse {

    @Schema(description = "ID giao dịch thanh toán")
    private UUID id;

    @Schema(description = "ID đơn hàng")
    private UUID orderId;

    @Schema(description = "Mã đơn hàng")
    private String orderCode;

    @Schema(description = "Phương thức thanh toán")
    private PaymentGateway method;

    @Schema(description = "Mã giao dịch từ cổng thanh toán")
    private String transactionId;

    @Schema(description = "Số tiền thanh toán")
    private BigDecimal amount;

    @Schema(description = "Trạng thái thanh toán (PENDING, SUCCESS, FAILED, CANCELLED, REFUNDED)")
    private PaymentStatus status;

    @Schema(description = "Thời điểm thanh toán thành công")
    private Instant paidAt;

    @Schema(description = "Thời điểm tạo giao dịch")
    private Instant createdAt;
}
