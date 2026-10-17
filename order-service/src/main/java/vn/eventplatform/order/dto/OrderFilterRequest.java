package vn.eventplatform.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.eventplatform.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tiêu chí tìm kiếm và lọc danh sách đơn hàng")
public class OrderFilterRequest {

    @Schema(description = "ID người dùng (Chỉ có hiệu lực với Admin/Staff để lọc theo user, user thường bị bỏ qua và gán bằng chính mình)")
    private UUID userId;

    @Schema(description = "Trạng thái đơn hàng (PENDING, PAID, CANCELLED, REFUNDED, EXPIRED)")
    private OrderStatus status;

    @Schema(description = "Mã đơn hàng (tìm kiếm gần đúng / contains)")
    private String orderCode;

    @Schema(description = "ID sự kiện")
    private UUID eventId;

    @Schema(description = "ID suất diễn")
    private UUID showTimeId;

    @Schema(description = "Từ khóa tìm kiếm (theo mã đơn hàng, tên khách hàng, email hoặc SĐT)")
    private String keyword;

    @Schema(description = "Thời gian tạo từ ngày (ISO-8601, ví dụ: 2026-10-17T00:00:00Z)")
    private Instant fromDate;

    @Schema(description = "Thời gian tạo đến ngày (ISO-8601, ví dụ: 2026-10-17T23:59:59Z)")
    private Instant toDate;
}
