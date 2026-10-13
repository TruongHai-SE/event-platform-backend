package vn.eventplatform.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.eventplatform.ticket.domain.CheckInMethod;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInResponse {

    private UUID id;
    private UUID ticketId;
    private UUID staffId;
    private CheckInMethod method;
    private String seatNumber;
    private Instant checkedInAt;
    private Instant createdAt;
}
