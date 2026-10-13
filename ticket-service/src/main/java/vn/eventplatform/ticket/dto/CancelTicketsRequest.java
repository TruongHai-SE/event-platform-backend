package vn.eventplatform.ticket.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelTicketsRequest {

    @NotEmpty(message = "Danh sách showTimeIds không được để trống")
    private List<UUID> showTimeIds;

    private String cancellationReason;
}
