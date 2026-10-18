package vn.eventplatform.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.eventplatform.order.domain.PaymentGatewayType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả khởi tạo lại liên kết thanh toán (đổi cổng) cho đơn hàng")
public class OrderPaymentInitResponse {

    @Schema(description = "ID của bản ghi Payment tạo mới")
    private UUID paymentId;

    @Schema(description = "ID đơn hàng")
    private UUID orderId;

    @Schema(description = "Mã đơn hàng")
    private String orderCode;

    @Schema(description = "Phương thức thanh toán đã chọn")
    private PaymentGatewayType method;

    @Schema(description = "Số tiền cần thanh toán (VNĐ)")
    private BigDecimal amount;

    @Schema(description = "Đường dẫn chuyển hướng thanh toán (VNPay, MoMo)")
    private String paymentUrl;

    @Schema(description = "Chuỗi mã QR thanh toán (SePay VietQR, MoMo QR)")
    private String qrCode;

    @Schema(description = "Trạng thái giao dịch thanh toán (PENDING)")
    private String status;

    @Schema(description = "Thời hạn hết hạn giữ chỗ thanh toán của đơn hàng")
    private Instant expiresAt;
}
