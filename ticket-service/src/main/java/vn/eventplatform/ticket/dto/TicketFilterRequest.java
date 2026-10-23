package vn.eventplatform.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.eventplatform.ticket.domain.TicketStatus;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tiêu chí tìm kiếm và lọc danh sách vé")
public class TicketFilterRequest {

    @Schema(description = "Từ khóa tìm kiếm (mã vé, mã đơn hàng, loại vé, số ghế, mã QR)")
    private String keyword;

    @Schema(description = "Trạng thái vé: SOLD, USED, CANCELLED")
    private TicketStatus status;

    @Schema(description = "ID của sự kiện")
    private UUID eventId;

    @Schema(description = "ID của suất diễn")
    private UUID showTimeId;

    @Schema(description = "ID của loại vé")
    private UUID ticketTypeId;

    @Schema(description = "ID của người dùng sở hữu vé")
    private UUID userId;

    @Schema(description = "ID của đơn hàng")
    private UUID orderId;
}
