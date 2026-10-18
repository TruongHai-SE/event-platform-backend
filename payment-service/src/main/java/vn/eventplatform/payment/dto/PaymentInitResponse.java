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
@Schema(description = "Kết quả khởi tạo liên kết thanh toán")
public class PaymentInitResponse {

    @Schema(description = "ID của bản ghi Payment tạo mới")
    private UUID paymentId;

    @Schema(description = "ID đơn hàng")
    private UUID orderId;

    @Schema(description = "Mã đơn hàng")
    private String orderCode;

    @Schema(description = "Phương thức thanh toán đã chọn")
    private PaymentGateway method;

    @Schema(description = "Số tiền cần thanh toán (VNĐ)")
    private BigDecimal amount;

    @Schema(description = "Đường dẫn chuyển hướng thanh toán (đối với VNPay, MoMo redirect)")
    private String paymentUrl;

    @Schema(description = "Chuỗi mã QR thanh toán (đối với VietQR/SePay, MoMo QR)")
    private String qrCode;

    @Schema(description = "Trạng thái thanh toán (PENDING)")
    private PaymentStatus status;

    @Schema(description = "Thời hạn hết hạn thanh toán")
    private Instant expiresAt;
}
