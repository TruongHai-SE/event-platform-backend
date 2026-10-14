package vn.eventplatform.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.ticket.domain.Ticket;
import vn.eventplatform.ticket.domain.TicketStatus;
import vn.eventplatform.ticket.dto.CancelTicketsRequest;
import vn.eventplatform.ticket.dto.CancelTicketsResponse;
import vn.eventplatform.ticket.dto.IssueTicketRequest;
import vn.eventplatform.ticket.dto.TicketResponse;
import vn.eventplatform.ticket.mapper.TicketMapper;
import vn.eventplatform.ticket.repository.TicketRepository;

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
}
