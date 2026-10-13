package vn.eventplatform.order.client.dto;

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
    private UUID showTimeId;
    private List<TicketItemReservationRequest> items;
}
