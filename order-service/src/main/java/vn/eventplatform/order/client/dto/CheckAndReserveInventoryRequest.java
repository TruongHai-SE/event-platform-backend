package vn.eventplatform.order.client.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckAndReserveInventoryRequest {

    @NotNull(message = "ID sự kiện không được để trống")
    private UUID eventId;

    @NotNull(message = "ID suất diễn không được để trống")
    private UUID showTimeId;

    @NotEmpty(message = "Danh sách hạng vé cần giữ chỗ không được để trống")
    @Valid
    private List<TicketItemReservationRequest> items;
}
