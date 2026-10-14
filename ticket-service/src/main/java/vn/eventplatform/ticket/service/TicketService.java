package vn.eventplatform.ticket.service;

import vn.eventplatform.ticket.dto.CancelTicketsRequest;
import vn.eventplatform.ticket.dto.CancelTicketsResponse;
import vn.eventplatform.ticket.dto.IssueTicketRequest;
import vn.eventplatform.ticket.dto.TicketResponse;

import java.util.List;
import java.util.UUID;

public interface TicketService {

    TicketResponse issueTicket(IssueTicketRequest request);

    List<TicketResponse> issueTickets(List<IssueTicketRequest> requests);

    CancelTicketsResponse cancelTicketsByShowTimes(CancelTicketsRequest request);

    TicketResponse cancelTicket(UUID ticketId, String reason);

    TicketResponse getTicketById(UUID id);

    TicketResponse getTicketByQr(String qrPayload);

    List<TicketResponse> getTicketsByOrderId(UUID orderId);
}
