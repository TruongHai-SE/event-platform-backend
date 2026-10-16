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
public class ReleaseInventoryRequest {

    @NotNull(message = "ID suất diễn không được để trống")
    private UUID showTimeId;

    @NotEmpty(message = "Danh sách hạng vé cần giải phóng không được để trống")
    @Valid
    private List<TicketItemReservationRequest> items;
}
