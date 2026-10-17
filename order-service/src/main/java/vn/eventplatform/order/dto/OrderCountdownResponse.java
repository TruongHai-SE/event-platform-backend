package vn.eventplatform.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.eventplatform.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin đếm ngược thời gian giữ chỗ vé của đơn hàng")
public class OrderCountdownResponse {

    @Schema(description = "ID của đơn hàng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID orderId;

    @Schema(description = "Mã đơn hàng", example = "TKZ-20261017-ABC12")
    private String orderCode;

    @Schema(description = "Trạng thái đơn hàng hiện tại", example = "PENDING")
    private OrderStatus status;

    @Schema(description = "Thời điểm hết hạn giữ chỗ (ISO-8601)", example = "2026-10-17T10:45:00Z")
    private Instant expiresAt;

    @Schema(description = "Thời gian hiện tại của máy chủ (ISO-8601, dùng đồng bộ client)", example = "2026-10-17T10:30:00Z")
    private Instant currentTime;

    @Schema(description = "Số giây giữ chỗ còn lại (0 nếu đã hết hạn hoặc không ở trạng thái PENDING)", example = "900")
    private Long remainingSeconds;

    @Schema(description = "Đơn hàng đã hết hạn giữ chỗ hay chưa", example = "false")
    private Boolean isExpired;
}
