package vn.eventplatform.ticket.dto;

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
public class CancelTicketsResponse {

    private int totalTicketsCancelled;
    private List<UUID> showTimeIds;
    private String message;
}
