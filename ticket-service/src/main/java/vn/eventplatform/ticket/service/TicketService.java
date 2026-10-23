package vn.eventplatform.ticket.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.eventplatform.common.messaging.event.OrderItemEventPayload;
import vn.eventplatform.ticket.dto.CancelTicketsRequest;
import vn.eventplatform.ticket.dto.CancelTicketsResponse;
import vn.eventplatform.ticket.dto.IssueTicketRequest;
import vn.eventplatform.ticket.dto.TicketFilterRequest;
import vn.eventplatform.ticket.dto.TicketResponse;

import java.util.List;
import java.util.UUID;

public interface TicketService {

    TicketResponse issueTicket(IssueTicketRequest request);

    List<TicketResponse> issueTickets(List<IssueTicketRequest> requests);

    default List<TicketResponse> issueTicketsForOrder(UUID orderId, UUID showTimeId, String orderCode, List<OrderItemEventPayload> items) {
        return issueTicketsForOrder(orderId, null, showTimeId, orderCode, items, null);
    }

    default List<TicketResponse> issueTicketsForOrder(UUID orderId, UUID eventId, UUID showTimeId, String orderCode, List<OrderItemEventPayload> items) {
        return issueTicketsForOrder(orderId, eventId, showTimeId, orderCode, items, null);
    }

    List<TicketResponse> issueTicketsForOrder(UUID orderId, UUID eventId, UUID showTimeId, String orderCode, List<OrderItemEventPayload> items, UUID userId);

    CancelTicketsResponse cancelTicketsByShowTimes(CancelTicketsRequest request);

    TicketResponse cancelTicket(UUID ticketId, String reason);

    TicketResponse getTicketById(UUID id);

    TicketResponse getTicketDetail(UUID id, UUID currentUserId, boolean isAdminOrStaff);

    TicketResponse getTicketByQr(String qrPayload);

    List<TicketResponse> getTicketsByOrderId(UUID orderId);

    List<TicketResponse> getTicketsByOrderId(UUID orderId, UUID currentUserId, boolean isAdminOrStaff);

    byte[] getTicketQrImage(UUID ticketId, int width, int height);

    byte[] getTicketQrImage(UUID ticketId, int width, int height, UUID currentUserId, boolean isAdminOrStaff);

    Page<TicketResponse> getMyTickets(UUID userId, TicketFilterRequest filter, Pageable pageable);

    Page<TicketResponse> searchTickets(TicketFilterRequest filter, Pageable pageable);

    boolean hasUsedTickets(UUID orderId);

    int cancelTicketsByOrderId(UUID orderId, String reason);
}
