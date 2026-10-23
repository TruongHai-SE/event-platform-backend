package vn.eventplatform.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.common.messaging.event.OrderItemEventPayload;
import vn.eventplatform.ticket.client.OrderServiceClient;
import vn.eventplatform.ticket.domain.Ticket;
import vn.eventplatform.ticket.domain.TicketStatus;
import vn.eventplatform.ticket.dto.*;
import vn.eventplatform.ticket.mapper.TicketMapper;
import vn.eventplatform.ticket.repository.TicketRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketMapper ticketMapper;
    private final TicketQrPayloadService ticketQrPayloadService;
    private final QrCodeGeneratorService qrCodeGeneratorService;

    @Autowired(required = false)
    private OrderServiceClient orderServiceClient;

    @Value("${security.internal.secret:internal-secret-key}")
    private String internalSecret;

    @Override
    @Transactional
    public TicketResponse issueTicket(IssueTicketRequest request) {
        if (request.getQrPayload() != null && !request.getQrPayload().isBlank()) {
            if (ticketRepository.existsByQrPayload(request.getQrPayload())) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Mã QR của vé đã tồn tại trong hệ thống");
            }
        }

        Ticket ticket = ticketMapper.toEntity(request);
        if (ticket.getId() == null) {
            ticket.setId(UUID.randomUUID());
        }

        if (ticket.getUserId() == null && request.getUserId() != null) {
            ticket.setUserId(request.getUserId());
        }
        if (ticket.getEventId() == null && request.getEventId() != null) {
            ticket.setEventId(request.getEventId());
        }
        if (ticket.getTicketTypeName() == null && request.getTicketTypeName() != null) {
            ticket.setTicketTypeName(request.getTicketTypeName());
        }

        if (ticket.getQrPayload() == null || ticket.getQrPayload().isBlank()) {
            TicketQrPayloadDto payloadDto = TicketQrPayloadDto.builder()
                    .ticketId(ticket.getId())
                    .eventId(ticket.getEventId())
                    .showTimeId(ticket.getShowTimeId())
                    .ticketTypeId(ticket.getTicketTypeId())
                    .ticketTypeName(ticket.getTicketTypeName())
                    .orderId(ticket.getOrderId())
                    .seatNumber(ticket.getSeatNumber())
                    .issuedAt(Instant.now())
                    .build();
            ticket.setQrPayload(ticketQrPayloadService.generatePayload(payloadDto));
        }

        ticket = ticketRepository.save(ticket);
        log.info("Đã phát hành vé thành công: id={}, orderId={}, showTimeId={}, userId={}",
                ticket.getId(), ticket.getOrderId(), ticket.getShowTimeId(), ticket.getUserId());
        return ticketMapper.toResponse(ticket);
    }

    @Override
    @Transactional
    public List<TicketResponse> issueTickets(List<IssueTicketRequest> requests) {
        return requests.stream().map(this::issueTicket).toList();
    }

    @Override
    @Transactional
    public List<TicketResponse> issueTicketsForOrder(UUID orderId, UUID eventId, UUID showTimeId, String orderCode, List<OrderItemEventPayload> items) {
        return issueTicketsForOrder(orderId, eventId, showTimeId, orderCode, items, null);
    }

    @Override
    @Transactional
    public List<TicketResponse> issueTicketsForOrder(UUID orderId, UUID eventId, UUID showTimeId, String orderCode, List<OrderItemEventPayload> items, UUID userId) {
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
                UUID ticketId = UUID.randomUUID();
                TicketQrPayloadDto payloadDto = TicketQrPayloadDto.builder()
                        .ticketId(ticketId)
                        .eventId(eventId)
                        .showTimeId(showTimeId)
                        .ticketTypeId(ticketTypeId)
                        .ticketTypeName(item.getTicketTypeName())
                        .orderId(orderId)
                        .seatNumber(null)
                        .issuedAt(Instant.now())
                        .build();

                String qrPayload = ticketQrPayloadService.generatePayload(payloadDto);
                Ticket ticket = Ticket.builder()
                        .id(ticketId)
                        .userId(userId)
                        .orderId(orderId)
                        .eventId(eventId)
                        .showTimeId(showTimeId)
                        .ticketTypeId(ticketTypeId)
                        .ticketTypeName(item.getTicketTypeName())
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
        log.info("Đã tự động phát hành thành công {} vé cho đơn hàng orderId={} (mã đơn: {}, userId: {})",
                savedTickets.size(), orderId, orderCode, userId);
        return savedTickets.stream().map(ticketMapper::toResponse).toList();
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
    @Transactional
    public TicketResponse getTicketDetail(UUID id, UUID currentUserId, boolean isAdminOrStaff) {
        Ticket ticket = ticketRepository.findByIdWithCheckIn(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin vé"));
        verifyTicketOwnership(ticket, currentUserId, isAdminOrStaff);
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
    @Transactional
    public List<TicketResponse> getTicketsByOrderId(UUID orderId, UUID currentUserId, boolean isAdminOrStaff) {
        List<Ticket> tickets = ticketRepository.findByOrderIdWithCheckIn(orderId);
        if (tickets.isEmpty()) {
            return List.of();
        }

        if (!isAdminOrStaff) {
            if (currentUserId == null) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Chưa xác thực người dùng");
            }
            UUID ticketUserId = tickets.stream()
                    .map(Ticket::getUserId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            if (ticketUserId != null) {
                if (!ticketUserId.equals(currentUserId)) {
                    throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem vé của đơn hàng này");
                }
            } else if (orderServiceClient != null) {
                try {
                    var orderResponse = orderServiceClient.getOrderById(internalSecret, orderId);
                    if (orderResponse != null && orderResponse.getData() != null) {
                        var order = orderResponse.getData();
                        tickets.forEach(t -> {
                            t.setUserId(order.getUserId());
                            if (t.getEventId() == null) {
                                t.setEventId(order.getEventId());
                            }
                        });
                        ticketRepository.saveAll(tickets);

                        if (!currentUserId.equals(order.getUserId())) {
                            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem vé của đơn hàng này");
                        }
                    }
                } catch (AppException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("Lỗi khi kiểm tra quyền sở hữu đơn hàng: {}", e.getMessage());
                    throw new AppException(ErrorCode.FORBIDDEN, "Không thể xác minh quyền sở hữu đơn hàng");
                }
            }
        }

        return tickets.stream().map(ticketMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getTicketQrImage(UUID ticketId, int width, int height) {
        return getTicketQrImage(ticketId, width, height, null, true);
    }

    @Override
    @Transactional
    public byte[] getTicketQrImage(UUID ticketId, int width, int height, UUID currentUserId, boolean isAdminOrStaff) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin vé"));
        verifyTicketOwnership(ticket, currentUserId, isAdminOrStaff);
        return qrCodeGeneratorService.generateQrPng(ticket.getQrPayload(), width, height);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> getMyTickets(UUID userId, TicketFilterRequest filter, Pageable pageable) {
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chưa xác thực người dùng");
        }

        Specification<Ticket> spec = TicketSpecification.filterCustomerTickets(userId, filter);
        Page<Ticket> page = ticketRepository.findAll(spec, pageable);
        return page.map(ticketMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> searchTickets(TicketFilterRequest filter, Pageable pageable) {
        Specification<Ticket> spec = TicketSpecification.filterAdminTickets(filter);
        Page<Ticket> page = ticketRepository.findAll(spec, pageable);
        return page.map(ticketMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUsedTickets(UUID orderId) {
        List<Ticket> tickets = ticketRepository.findByOrderIdWithCheckIn(orderId);
        return tickets.stream().anyMatch(t -> t.getCheckIn() != null || t.getStatus() == TicketStatus.USED);
    }

    @Override
    @Transactional
    public int cancelTicketsByOrderId(UUID orderId, String reason) {
        List<Ticket> tickets = ticketRepository.findByOrderId(orderId);
        if (tickets.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Ticket ticket : tickets) {
            if (ticket.getStatus() != TicketStatus.CANCELLED) {
                ticket.setStatus(TicketStatus.CANCELLED);
                count++;
            }
        }
        ticketRepository.saveAll(tickets);
        log.info("Đã hủy {} vé của đơn hàng orderId={}, lý do: {}", count, orderId, reason);
        return count;
    }

    private void verifyTicketOwnership(Ticket ticket, UUID currentUserId, boolean isAdminOrStaff) {
        if (isAdminOrStaff) {
            return;
        }
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Chưa xác thực người dùng");
        }
        if (ticket.getUserId() != null) {
            if (!ticket.getUserId().equals(currentUserId)) {
                log.warn("Từ chối truy cập vé: userId={} không phải chủ sở hữu của vé id={}", currentUserId, ticket.getId());
                throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập vé này");
            }
            return;
        }

        // Fallback kiểm tra qua order-service cho các vé cũ chưa có user_id
        if (ticket.getOrderId() != null && orderServiceClient != null) {
            try {
                var orderResponse = orderServiceClient.getOrderById(internalSecret, ticket.getOrderId());
                if (orderResponse != null && orderResponse.getData() != null) {
                    var order = orderResponse.getData();
                    ticket.setUserId(order.getUserId());
                    if (ticket.getEventId() == null) {
                        ticket.setEventId(order.getEventId());
                    }
                    ticketRepository.save(ticket);

                    if (!currentUserId.equals(order.getUserId())) {
                        log.warn("Từ chối truy cập vé: userId={} không khớp order.userId={} cho vé id={}",
                                currentUserId, order.getUserId(), ticket.getId());
                        throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập vé này");
                    }
                    return;
                }
            } catch (AppException e) {
                throw e;
            } catch (Exception e) {
                log.error("Lỗi khi kiểm tra quyền sở hữu vé qua order-service: {}", e.getMessage());
            }
        }

        throw new AppException(ErrorCode.FORBIDDEN, "Không thể xác minh quyền sở hữu vé");
    }
}
