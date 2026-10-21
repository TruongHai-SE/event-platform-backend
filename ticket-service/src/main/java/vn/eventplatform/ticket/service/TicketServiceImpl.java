package vn.eventplatform.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.common.messaging.event.OrderItemEventPayload;
import vn.eventplatform.ticket.domain.Ticket;
import vn.eventplatform.ticket.domain.TicketStatus;
import vn.eventplatform.ticket.dto.CancelTicketsRequest;
import vn.eventplatform.ticket.dto.CancelTicketsResponse;
import vn.eventplatform.ticket.dto.IssueTicketRequest;
import vn.eventplatform.ticket.dto.TicketResponse;
import vn.eventplatform.ticket.mapper.TicketMapper;
import vn.eventplatform.ticket.repository.TicketRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketMapper ticketMapper;

    @Override
    @Transactional
    public TicketResponse issueTicket(IssueTicketRequest request) {
        if (ticketRepository.existsByQrPayload(request.getQrPayload())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã QR của vé đã tồn tại trong hệ thống");
        }

        Ticket ticket = ticketMapper.toEntity(request);
        ticket = ticketRepository.save(ticket);
        log.info("Đã phát hành vé thành công: id={}, orderId={}, showTimeId={}",
                ticket.getId(), ticket.getOrderId(), ticket.getShowTimeId());
        return ticketMapper.toResponse(ticket);
    }

    @Override
    @Transactional
    public List<TicketResponse> issueTickets(List<IssueTicketRequest> requests) {
        return requests.stream().map(this::issueTicket).toList();
    }

    @Override
    @Transactional
    public List<TicketResponse> issueTicketsForOrder(UUID orderId, UUID showTimeId, String orderCode, List<OrderItemEventPayload> items) {
        if (orderId == null) {
            log.warn("Không thể phát hành vé: orderId là null");
            return List.of();
        }

        if (items == null || items.isEmpty()) {
            log.warn("Không thể phát hành vé: danh sách items của đơn hàng orderId={} là rỗng", orderId);
            return List.of();
        }

        // Kiểm tra tính Idempotent ở mức Database: nếu vé của đơn hàng này đã được phát hành trước đó
        if (ticketRepository.existsByOrderId(orderId)) {
            log.info("Vé cho đơn hàng orderId={} ({}) đã được phát hành trước đó (idempotent), trả về danh sách vé hiện có",
                    orderId, orderCode);
            return getTicketsByOrderId(orderId);
        }

        List<Ticket> newTickets = new ArrayList<>();
        for (OrderItemEventPayload item : items) {
            int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
            UUID ticketTypeId = item.getTicketTypeId();

            if (ticketTypeId == null || quantity <= 0) {
                log.warn("Bỏ qua item không hợp lệ: ticketTypeId={}, quantity={}", ticketTypeId, quantity);
                continue;
            }

            for (int i = 0; i < quantity; i++) {
                String qrPayload = generateUniqueQrPayload(orderCode, orderId, ticketTypeId);
                Ticket ticket = Ticket.builder()
                        .orderId(orderId)
                        .showTimeId(showTimeId)
                        .ticketTypeId(ticketTypeId)
                        .status(TicketStatus.SOLD)
                        .qrPayload(qrPayload)
                        .build();
                newTickets.add(ticket);
            }
        }

        if (newTickets.isEmpty()) {
            log.warn("Không có vé hợp lệ nào được tạo cho orderId={}", orderId);
            return List.of();
        }

        List<Ticket> savedTickets = ticketRepository.saveAll(newTickets);
        log.info("Đã tự động phát hành thành công {} vé cho đơn hàng orderId={} (mã đơn: {})",
                savedTickets.size(), orderId, orderCode);
        return savedTickets.stream().map(ticketMapper::toResponse).toList();
    }

    private String generateUniqueQrPayload(String orderCode, UUID orderId, UUID ticketTypeId) {
        String codePart = (orderCode != null && !orderCode.isBlank())
                ? orderCode.trim()
                : orderId.toString().substring(0, 8);
        String qrPayload;
        do {
            qrPayload = "TKZ-QR-" + codePart + "-" + UUID.randomUUID().toString().replace("-", "");
        } while (ticketRepository.existsByQrPayload(qrPayload));
        return qrPayload;
    }

    @Override
    @Transactional
    public CancelTicketsResponse cancelTicketsByShowTimes(CancelTicketsRequest request) {
        log.warn("Khóa toàn bộ vé do sự kiện bị hủy: suất diễn={}, lý do: {}",
                request.getShowTimeIds(), request.getCancellationReason());

        int count = ticketRepository.cancelTicketsByShowTimeIds(
                request.getShowTimeIds(),
                TicketStatus.CANCELLED
        );

        log.info("Đã khóa thành công {} vé do sự kiện bị hủy", count);
        return CancelTicketsResponse.builder()
                .totalTicketsCancelled(count)
                .showTimeIds(request.getShowTimeIds())
                .message("Đã khóa thành công " + count + " vé do sự kiện bị hủy")
                .build();
    }

    @Override
    @Transactional
    public TicketResponse cancelTicket(UUID ticketId, String reason) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin vé"));

        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            return ticketMapper.toResponse(ticket);
        }

        ticket.setStatus(TicketStatus.CANCELLED);
        ticket = ticketRepository.save(ticket);
        log.info("Vé id={} đã được chuyển sang trạng thái CANCELLED, lý do: {}", ticketId, reason);
        return ticketMapper.toResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicketById(UUID id) {
        Ticket ticket = ticketRepository.findByIdWithCheckIn(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin vé"));
        return ticketMapper.toResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicketByQr(String qrPayload) {
        Ticket ticket = ticketRepository.findByQrPayloadWithCheckIn(qrPayload)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin vé từ mã QR"));
        return ticketMapper.toResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponse> getTicketsByOrderId(UUID orderId) {
        List<Ticket> tickets = ticketRepository.findByOrderIdWithCheckIn(orderId);
        return tickets.stream().map(ticketMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUsedTickets(UUID orderId) {
        return ticketRepository.countByOrderIdAndStatus(orderId, TicketStatus.USED) > 0;
    }

    @Override
    @Transactional
    public int cancelTicketsByOrderId(UUID orderId, String reason) {
        log.info("Hủy toàn bộ vé của đơn hàng orderId={}, lý do: {}", orderId, reason);
        int count = ticketRepository.cancelTicketsByOrderId(orderId, TicketStatus.CANCELLED);
        log.info("Đã hủy thành công {} vé của đơn hàng orderId={}", count, orderId);
        return count;
    }
}
