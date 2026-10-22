package vn.eventplatform.ticket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.ticket.domain.CheckIn;
import vn.eventplatform.ticket.domain.CheckInMethod;
import vn.eventplatform.ticket.domain.Ticket;
import vn.eventplatform.ticket.domain.TicketStatus;
import vn.eventplatform.ticket.dto.CancelTicketsRequest;
import vn.eventplatform.ticket.dto.CancelTicketsResponse;
import vn.eventplatform.ticket.dto.CheckInRequest;
import vn.eventplatform.ticket.dto.CheckInResponse;
import vn.eventplatform.ticket.mapper.CheckInMapper;
import vn.eventplatform.ticket.mapper.TicketMapper;
import vn.eventplatform.ticket.repository.CheckInRepository;
import vn.eventplatform.ticket.repository.TicketRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketCancellationAndCheckInTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private CheckInMapper checkInMapper;

    @Mock
    private TicketQrPayloadService ticketQrPayloadService;

    @Mock
    private QrCodeGeneratorService qrCodeGeneratorService;

    private TicketServiceImpl ticketService;
    private CheckInServiceImpl checkInService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketServiceImpl(ticketRepository, ticketMapper, ticketQrPayloadService, qrCodeGeneratorService);
        checkInService = new CheckInServiceImpl(ticketRepository, checkInRepository, checkInMapper);
    }

    @Test
    @DisplayName("Khóa toàn bộ vé của sự kiện khi các suất diễn bị hủy")
    void shouldCancelAllTicketsWhenEventIsCancelled() {
        UUID showTime1 = UUID.randomUUID();
        UUID showTime2 = UUID.randomUUID();
        List<UUID> showTimeIds = List.of(showTime1, showTime2);

        when(ticketRepository.cancelTicketsByShowTimeIds(showTimeIds, TicketStatus.CANCELLED))
                .thenReturn(150);

        CancelTicketsRequest request = CancelTicketsRequest.builder()
                .showTimeIds(showTimeIds)
                .cancellationReason("Ban tổ chức thông báo hủy show do thời tiết")
                .build();

        CancelTicketsResponse response = ticketService.cancelTicketsByShowTimes(request);

        assertThat(response).isNotNull();
        assertThat(response.getTotalTicketsCancelled()).isEqualTo(150);
        assertThat(response.getShowTimeIds()).containsExactlyElementsOf(showTimeIds);
        assertThat(response.getMessage()).contains("150");

        verify(ticketRepository).cancelTicketsByShowTimeIds(showTimeIds, TicketStatus.CANCELLED);
    }

    @Test
    @DisplayName("Từ chối check-in khi vé thuộc sự kiện đã bị hủy (status = CANCELLED)")
    void shouldRejectCheckInWhenTicketIsCancelled() {
        UUID ticketId = UUID.randomUUID();
        String qrPayload = "qr-cancelled-ticket";
        UUID staffId = UUID.randomUUID();

        Ticket cancelledTicket = Ticket.builder()
                .id(ticketId)
                .qrPayload(qrPayload)
                .status(TicketStatus.CANCELLED)
                .build();

        when(ticketRepository.findByQrPayloadWithCheckIn(qrPayload))
                .thenReturn(Optional.of(cancelledTicket));

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload(qrPayload)
                .staffId(staffId)
                .method(CheckInMethod.QR_SCAN)
                .build();

        assertThatThrownBy(() -> checkInService.processCheckIn(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(appEx.getMessage()).contains("Vé đã bị khóa do sự kiện bị hủy");
                });

        verify(checkInRepository, never()).save(any());
    }

    @Test
    @DisplayName("Từ chối check-in khi vé đã được check-in trước đó (chống double check-in)")
    void shouldRejectCheckInWhenTicketAlreadyUsed() {
        UUID ticketId = UUID.randomUUID();
        String qrPayload = "qr-used-ticket";
        UUID staffId = UUID.randomUUID();

        Ticket usedTicket = Ticket.builder()
                .id(ticketId)
                .qrPayload(qrPayload)
                .status(TicketStatus.USED)
                .build();

        when(ticketRepository.findByQrPayloadWithCheckIn(qrPayload))
                .thenReturn(Optional.of(usedTicket));

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload(qrPayload)
                .staffId(staffId)
                .method(CheckInMethod.QR_SCAN)
                .build();

        assertThatThrownBy(() -> checkInService.processCheckIn(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(appEx.getMessage()).contains("Vé đã được sử dụng check-in trước đó");
                });

        verify(checkInRepository, never()).save(any());
    }

    @Test
    @DisplayName("Check-in thành công cho vé hợp lệ và chuyển trạng thái sang USED")
    void shouldSuccessfullyCheckInValidTicket() {
        UUID ticketId = UUID.randomUUID();
        String qrPayload = "qr-valid-ticket";
        UUID staffId = UUID.randomUUID();

        Ticket validTicket = Ticket.builder()
                .id(ticketId)
                .qrPayload(qrPayload)
                .status(TicketStatus.SOLD)
                .seatNumber("VIP-A01")
                .build();

        CheckIn savedCheckIn = CheckIn.builder()
                .id(UUID.randomUUID())
                .ticket(validTicket)
                .staffId(staffId)
                .method(CheckInMethod.QR_SCAN)
                .checkedInAt(Instant.now())
                .build();

        CheckInResponse expectedResponse = CheckInResponse.builder()
                .id(savedCheckIn.getId())
                .ticketId(ticketId)
                .staffId(staffId)
                .method(CheckInMethod.QR_SCAN)
                .seatNumber("VIP-A01")
                .build();

        when(ticketRepository.findByQrPayloadWithCheckIn(qrPayload)).thenReturn(Optional.of(validTicket));
        when(checkInRepository.existsByTicketId(ticketId)).thenReturn(false);
        when(checkInMapper.toEntity(validTicket, staffId, CheckInMethod.QR_SCAN)).thenReturn(savedCheckIn);
        when(checkInRepository.save(savedCheckIn)).thenReturn(savedCheckIn);
        when(checkInMapper.toResponse(savedCheckIn)).thenReturn(expectedResponse);

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload(qrPayload)
                .staffId(staffId)
                .method(CheckInMethod.QR_SCAN)
                .build();

        CheckInResponse response = checkInService.processCheckIn(request);

        assertThat(response).isNotNull();
        assertThat(response.getTicketId()).isEqualTo(ticketId);
        assertThat(validTicket.getStatus()).isEqualTo(TicketStatus.USED);

        verify(ticketRepository).save(validTicket);
        verify(checkInRepository).save(savedCheckIn);
    }
}
