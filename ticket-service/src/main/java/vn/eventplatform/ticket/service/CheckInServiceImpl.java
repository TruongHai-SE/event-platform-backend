package vn.eventplatform.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.ticket.domain.CheckIn;
import vn.eventplatform.ticket.domain.Ticket;
import vn.eventplatform.ticket.domain.TicketStatus;
import vn.eventplatform.ticket.dto.CheckInRequest;
import vn.eventplatform.ticket.dto.CheckInResponse;
import vn.eventplatform.ticket.mapper.CheckInMapper;
import vn.eventplatform.ticket.repository.CheckInRepository;
import vn.eventplatform.ticket.repository.TicketRepository;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {

    private final TicketRepository ticketRepository;
    private final CheckInRepository checkInRepository;
    private final CheckInMapper checkInMapper;

    @Override
    @Transactional
    public CheckInResponse processCheckIn(CheckInRequest request) {
        Ticket ticket = findTicket(request);

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

        // 3. Đánh dấu vé đã sử dụng
        ticket.setStatus(TicketStatus.USED);
        ticketRepository.save(ticket);

        // 4. Lưu bản ghi check-in
        CheckIn checkIn = checkInMapper.toEntity(ticket, request.getStaffId(), request.getMethod());
        checkIn = checkInRepository.save(checkIn);

        log.info("Check-in thành công: ticketId={}, staffId={}, method={}",
                ticket.getId(), request.getStaffId(), request.getMethod());
        return checkInMapper.toResponse(checkIn);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckInResponse getCheckInByTicketId(UUID ticketId) {
        CheckIn checkIn = checkInRepository.findByTicketIdWithTicket(ticketId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Vé này chưa được check-in"));
        return checkInMapper.toResponse(checkIn);
    }

    private Ticket findTicket(CheckInRequest request) {
        if (request.getQrPayload() != null && !request.getQrPayload().isBlank()) {
            return ticketRepository.findByQrPayloadWithCheckIn(request.getQrPayload())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Mã QR không hợp lệ hoặc không tồn tại"));
        } else if (request.getTicketId() != null) {
            return ticketRepository.findByIdWithCheckIn(request.getTicketId())
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin vé"));
        } else {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cần cung cấp mã QR hoặc ID vé để check-in");
        }
    }
}
