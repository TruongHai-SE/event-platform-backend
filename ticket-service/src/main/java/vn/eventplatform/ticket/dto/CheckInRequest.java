package vn.eventplatform.ticket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.eventplatform.ticket.domain.CheckInMethod;

import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CheckInRequest {

    private String qrPayload;

    private UUID ticketId;

    private UUID staffId;

    private UUID eventId;

    private UUID showTimeId;

    @Builder.Default
    private CheckInMethod method = CheckInMethod.QR_SCAN;
}
