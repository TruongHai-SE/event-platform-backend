package vn.eventplatform.ticket.service;

import vn.eventplatform.common.messaging.event.OrderItemEventPayload;
import vn.eventplatform.ticket.dto.CancelTicketsRequest;
import vn.eventplatform.ticket.dto.CancelTicketsResponse;
import vn.eventplatform.ticket.dto.IssueTicketRequest;
import vn.eventplatform.ticket.dto.TicketResponse;

import java.util.List;
import java.util.UUID;

public interface TicketService {

    TicketResponse issueTicket(IssueTicketRequest request);

    List<TicketResponse> issueTickets(List<IssueTicketRequest> requests);

    default List<TicketResponse> issueTicketsForOrder(UUID orderId, UUID showTimeId, String orderCode, List<OrderItemEventPayload> items) {
        return issueTicketsForOrder(orderId, null, showTimeId, orderCode, items);
    }

    List<TicketResponse> issueTicketsForOrder(UUID orderId, UUID eventId, UUID showTimeId, String orderCode, List<OrderItemEventPayload> items);

    CancelTicketsResponse cancelTicketsByShowTimes(CancelTicketsRequest request);

    TicketResponse cancelTicket(UUID ticketId, String reason);

    TicketResponse getTicketById(UUID id);

    TicketResponse getTicketByQr(String qrPayload);

    List<TicketResponse> getTicketsByOrderId(UUID orderId);

    byte[] getTicketQrImage(UUID ticketId, int width, int height);
}
