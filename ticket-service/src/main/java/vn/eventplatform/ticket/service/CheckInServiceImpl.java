package vn.eventplatform.ticket.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.ticket.client.OrderServiceClient;
import vn.eventplatform.ticket.domain.CheckIn;
import vn.eventplatform.ticket.domain.Ticket;
import vn.eventplatform.ticket.domain.TicketStatus;
import vn.eventplatform.ticket.dto.CheckInRequest;
import vn.eventplatform.ticket.dto.CheckInResponse;
import vn.eventplatform.ticket.dto.TicketQrPayloadDto;
import vn.eventplatform.ticket.dto.TicketResponse;
import vn.eventplatform.ticket.mapper.CheckInMapper;
import vn.eventplatform.ticket.mapper.TicketMapper;
import vn.eventplatform.ticket.repository.CheckInRepository;
import vn.eventplatform.ticket.repository.TicketRepository;

import java.util.UUID;

@Slf4j
@Service
public class CheckInServiceImpl implements CheckInService {

    private final TicketRepository ticketRepository;
    private final CheckInRepository checkInRepository;
    private final CheckInMapper checkInMapper;
    private final TicketQrPayloadService ticketQrPayloadService;
    private final TicketMapper ticketMapper;
    private final OrderServiceClient orderServiceClient;

    @Value("${internal.secret:internal-secret-token}")
    private String internalSecret = "internal-secret-token";

    public CheckInServiceImpl(TicketRepository ticketRepository,
                              CheckInRepository checkInRepository,
                              CheckInMapper checkInMapper,
                              TicketQrPayloadService ticketQrPayloadService,
                              @Autowired(required = false) TicketMapper ticketMapper,
                              @Autowired(required = false) OrderServiceClient orderServiceClient) {
        this.ticketRepository = ticketRepository;
        this.checkInRepository = checkInRepository;
        this.checkInMapper = checkInMapper;
        this.ticketQrPayloadService = ticketQrPayloadService;
        this.ticketMapper = ticketMapper;
        this.orderServiceClient = orderServiceClient;
    }

    public CheckInServiceImpl(TicketRepository ticketRepository,
                              CheckInRepository checkInRepository,
                              CheckInMapper checkInMapper,
                              TicketQrPayloadService ticketQrPayloadService) {
        this(ticketRepository, checkInRepository, checkInMapper, ticketQrPayloadService, null, null);
    }

    @Override
    @Transactional
    public CheckInResponse processCheckIn(CheckInRequest request) {
        Ticket ticket = findTicket(request);
        validateTicketEligibility(ticket, request);

        // Đánh dấu vé đã sử dụng
        ticket.setStatus(TicketStatus.USED);
        ticketRepository.save(ticket);

        // Lưu bản ghi check-in
        CheckIn checkIn = checkInMapper.toEntity(ticket, request.getStaffId(), request.getMethod());
        checkIn = checkInRepository.save(checkIn);

        log.info("Check-in thành công: ticketId={}, staffId={}, method={}, eventId={}, showTimeId={}",
                ticket.getId(), request.getStaffId(), request.getMethod(), ticket.getEventId(), ticket.getShowTimeId());
        return checkInMapper.toResponse(checkIn);
    }

    @Override
    @Transactional
    public TicketResponse validateTicket(CheckInRequest request) {
        Ticket ticket = findTicket(request);
        validateTicketEligibility(ticket, request);

        if (ticketMapper != null) {
            return ticketMapper.toResponse(ticket);
        }
        return TicketResponse.builder()
                .id(ticket.getId())
                .orderId(ticket.getOrderId())
                .userId(ticket.getUserId())
                .eventId(ticket.getEventId())
                .showTimeId(ticket.getShowTimeId())
                .ticketTypeId(ticket.getTicketTypeId())
                .ticketTypeName(ticket.getTicketTypeName())
                .seatNumber(ticket.getSeatNumber())
                .status(ticket.getStatus())
                .qrImageUrl(ticket.getId() != null ? "/api/v1/tickets/" + ticket.getId() + "/qr-code" : null)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckInResponse getCheckInByTicketId(UUID ticketId) {
        CheckIn checkIn = checkInRepository.findByTicketIdWithTicket(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Vé này chưa được check-in"));
        return checkInMapper.toResponse(checkIn);
    }

    private void validateTicketEligibility(Ticket ticket, CheckInRequest request) {
        // 1. Kiểm tra nếu vé bị khóa do sự kiện bị hủy hoặc đã hoàn tiền
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            log.warn("Từ chối check-in: Vé id={} đã bị khóa hoặc sự kiện đã bị hủy", ticket.getId());
            throw new AppException(ErrorCode.BAD_REQUEST, "Vé đã bị khóa do sự kiện bị hủy hoặc đã được hoàn tiền");
        }

        // 2. Chống double check-in: Kiểm tra vé đã dùng hoặc đã có trong bảng check_ins
        if (ticket.getStatus() == TicketStatus.USED || checkInRepository.existsByTicketId(ticket.getId())) {
            log.warn("Từ chối check-in: Vé id={} đã được check-in trước đó", ticket.getId());
            throw new AppException(ErrorCode.BAD_REQUEST, "Vé đã được sử dụng check-in trước đó");
        }

        // 3. Trạng thái vé phải là SOLD
        if (ticket.getStatus() != TicketStatus.SOLD) {
            log.warn("Từ chối check-in: Vé id={} trạng thái không hợp lệ ({})", ticket.getId(), ticket.getStatus());
            throw new AppException(ErrorCode.BAD_REQUEST, "Vé không ở trạng thái hợp lệ để check-in");
        }

        // 4. Kiểm tra sự kiện (eventId): Từ chối nếu cổng soát vé chỉ định eventId mà vé không khớp
        if (request.getEventId() != null) {
            ensureTicketEventId(ticket);
            if (ticket.getEventId() != null && !request.getEventId().equals(ticket.getEventId())) {
                log.warn("Từ chối check-in: Vé id={} thuộc eventId={} nhưng yêu cầu check-in tại eventId={}",
                        ticket.getId(), ticket.getEventId(), request.getEventId());
                throw new AppException(ErrorCode.BAD_REQUEST, "Vé không thuộc sự kiện này");
            }
        }

        // 5. Kiểm tra suất diễn (showTimeId): Từ chối nếu cổng soát vé chỉ định showTimeId mà vé không khớp
        if (request.getShowTimeId() != null && ticket.getShowTimeId() != null && !request.getShowTimeId().equals(ticket.getShowTimeId())) {
            log.warn("Từ chối check-in: Vé id={} thuộc showTimeId={} nhưng yêu cầu check-in tại showTimeId={}",
                    ticket.getId(), ticket.getShowTimeId(), request.getShowTimeId());
            throw new AppException(ErrorCode.BAD_REQUEST, "Vé không thuộc suất diễn này");
        }
    }

    private void ensureTicketEventId(Ticket ticket) {
        if (ticket.getEventId() == null && ticket.getOrderId() != null && orderServiceClient != null) {
            try {
                var orderResponse = orderServiceClient.getOrderById(internalSecret, ticket.getOrderId());
                if (orderResponse != null && orderResponse.getData() != null) {
                    ticket.setEventId(orderResponse.getData().getEventId());
                    ticketRepository.save(ticket);
                }
            } catch (Exception e) {
                log.warn("Không thể lấy eventId từ order-service cho vé id={}: {}", ticket.getId(), e.getMessage());
            }
        }
    }

    private Ticket findTicket(CheckInRequest request) {
        if (request.getQrPayload() != null && !request.getQrPayload().isBlank()) {
            String qr = request.getQrPayload().trim();
            // Nếu là chuỗi ký số JWT (3 phần phân tách bởi dấu chấm)
            if (qr.contains(".") && qr.split("\\.").length == 3) {
                TicketQrPayloadDto payload = ticketQrPayloadService.verifyAndParsePayload(qr);
                Ticket ticket = ticketRepository.findByIdWithCheckIn(payload.getTicketId())
                        .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin vé từ mã QR"));

                // Chống can thiệp: Đối chiếu thông tin sự kiện trong mã QR với dữ liệu vé thực tế
                if (payload.getEventId() != null && ticket.getEventId() != null && !payload.getEventId().equals(ticket.getEventId())) {
                    log.warn("Từ chối check-in: Mã QR eventId={} không khớp với vé eventId={}",
                            payload.getEventId(), ticket.getEventId());
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã QR không khớp thông tin sự kiện của vé");
                }

                // Chống can thiệp: Đối chiếu thông tin suất diễn trong mã QR với dữ liệu vé thực tế
                if (payload.getShowTimeId() != null && ticket.getShowTimeId() != null && !payload.getShowTimeId().equals(ticket.getShowTimeId())) {
                    log.warn("Từ chối check-in: Mã QR showTimeId={} không khớp với vé showTimeId={}",
                            payload.getShowTimeId(), ticket.getShowTimeId());
                    throw new AppException(ErrorCode.BAD_REQUEST, "Mã QR không khớp thông tin suất diễn của vé");
                }

                // Bổ sung thông tin từ token hợp lệ nếu DB còn thiếu
                if (ticket.getEventId() == null && payload.getEventId() != null) {
                    ticket.setEventId(payload.getEventId());
                    if (ticket.getTicketTypeName() == null) {
                        ticket.setTicketTypeName(payload.getTicketTypeName());
                    }
                    ticketRepository.save(ticket);
                }

                return ticket;
            }
            return ticketRepository.findByQrPayloadWithCheckIn(qr)
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Mã QR không hợp lệ hoặc không tồn tại"));
        } else if (request.getTicketId() != null) {
            return ticketRepository.findByIdWithCheckIn(request.getTicketId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin vé"));
        } else {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cần cung cấp mã QR hoặc ID vé để check-in");
        }
    }
}
