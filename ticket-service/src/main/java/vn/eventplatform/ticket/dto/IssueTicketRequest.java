package vn.eventplatform.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueTicketRequest {

    private UUID userId;

    private UUID eventId;

    @NotNull(message = "ticketTypeId không được để trống")
    private UUID ticketTypeId;

    private String ticketTypeName;

    @NotNull(message = "showTimeId không được để trống")
    private UUID showTimeId;

    @NotNull(message = "orderId không được để trống")
    private UUID orderId;

    private UUID seatId;

    private String seatNumber;

    @NotBlank(message = "qrPayload không được để trống")
    private String qrPayload;
}
