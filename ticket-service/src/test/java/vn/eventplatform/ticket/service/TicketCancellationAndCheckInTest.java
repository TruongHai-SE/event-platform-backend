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
        checkInService = new CheckInServiceImpl(ticketRepository, checkInRepository, checkInMapper, ticketQrPayloadService);
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

    @Test
    @DisplayName("Check-in thành công với mã QR JWT ký số hợp lệ: giải mã lấy ticketId và đổi status sang USED")
    void shouldCheckInSuccessfullyWithSignedJwtQrPayload() {
        UUID ticketId = UUID.randomUUID();
        String jwtQrPayload = "header.payload.signature";
        UUID staffId = UUID.randomUUID();

        vn.eventplatform.ticket.dto.TicketQrPayloadDto payloadDto = vn.eventplatform.ticket.dto.TicketQrPayloadDto.builder()
                .ticketId(ticketId)
                .build();

        Ticket validTicket = Ticket.builder()
                .id(ticketId)
                .qrPayload(jwtQrPayload)
                .status(TicketStatus.SOLD)
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
                .build();

        when(ticketQrPayloadService.verifyAndParsePayload(jwtQrPayload)).thenReturn(payloadDto);
        when(ticketRepository.findByIdWithCheckIn(ticketId)).thenReturn(Optional.of(validTicket));
        when(checkInRepository.existsByTicketId(ticketId)).thenReturn(false);
        when(checkInMapper.toEntity(validTicket, staffId, CheckInMethod.QR_SCAN)).thenReturn(savedCheckIn);
        when(checkInRepository.save(savedCheckIn)).thenReturn(savedCheckIn);
        when(checkInMapper.toResponse(savedCheckIn)).thenReturn(expectedResponse);

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload(jwtQrPayload)
                .staffId(staffId)
                .method(CheckInMethod.QR_SCAN)
                .build();

        CheckInResponse response = checkInService.processCheckIn(request);

        assertThat(response).isNotNull();
        assertThat(response.getTicketId()).isEqualTo(ticketId);
        assertThat(validTicket.getStatus()).isEqualTo(TicketStatus.USED);

        verify(ticketQrPayloadService).verifyAndParsePayload(jwtQrPayload);
        verify(ticketRepository).save(validTicket);
        verify(checkInRepository).save(savedCheckIn);
    }

    @Test
    @DisplayName("Từ chối check-in khi mã QR JWT bị làm giả hoặc sai chữ ký số HMAC-SHA256")
    void shouldRejectCheckInWhenJwtSignatureIsInvalid() {
        String tamperedQr = "tampered.jwt.token";
        UUID staffId = UUID.randomUUID();

        when(ticketQrPayloadService.verifyAndParsePayload(tamperedQr))
                .thenThrow(new AppException(vn.eventplatform.common.dto.ErrorCode.BAD_REQUEST, "Mã QR vé không hợp lệ hoặc đã bị can thiệp"));

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload(tamperedQr)
                .staffId(staffId)
                .build();

        assertThatThrownBy(() -> checkInService.processCheckIn(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Mã QR vé không hợp lệ hoặc đã bị can thiệp");

        verify(checkInRepository, never()).save(any());
    }

    @Test
    @DisplayName("Từ chối check-in khi quét vé sai sự kiện (eventId không khớp)")
    void shouldRejectCheckInWhenEventIdDoesNotMatch() {
        UUID ticketId = UUID.randomUUID();
        UUID actualEventId = UUID.randomUUID();
        UUID requestedEventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        String qrPayload = "qr-wrong-event";
        UUID staffId = UUID.randomUUID();

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .qrPayload(qrPayload)
                .eventId(actualEventId)
                .showTimeId(showTimeId)
                .status(TicketStatus.SOLD)
                .build();

        when(ticketRepository.findByQrPayloadWithCheckIn(qrPayload)).thenReturn(Optional.of(ticket));

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload(qrPayload)
                .staffId(staffId)
                .eventId(requestedEventId)
                .showTimeId(showTimeId)
                .build();

        assertThatThrownBy(() -> checkInService.processCheckIn(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(appEx.getMessage()).contains("Vé không thuộc sự kiện này");
                });

        verify(checkInRepository, never()).save(any());
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.SOLD);
    }

    @Test
    @DisplayName("Từ chối check-in khi quét vé sai suất diễn (showTimeId không khớp)")
    void shouldRejectCheckInWhenShowTimeIdDoesNotMatch() {
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID actualShowTimeId = UUID.randomUUID();
        UUID requestedShowTimeId = UUID.randomUUID();
        String qrPayload = "qr-wrong-showtime";
        UUID staffId = UUID.randomUUID();

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .qrPayload(qrPayload)
                .eventId(eventId)
                .showTimeId(actualShowTimeId)
                .status(TicketStatus.SOLD)
                .build();

        when(ticketRepository.findByQrPayloadWithCheckIn(qrPayload)).thenReturn(Optional.of(ticket));

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload(qrPayload)
                .staffId(staffId)
                .eventId(eventId)
                .showTimeId(requestedShowTimeId)
                .build();

        assertThatThrownBy(() -> checkInService.processCheckIn(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(appEx.getMessage()).contains("Vé không thuộc suất diễn này");
                });

        verify(checkInRepository, never()).save(any());
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.SOLD);
    }

    @Test
    @DisplayName("Từ chối check-in khi mã QR JWT bị can thiệp sai thông tin eventId")
    void shouldRejectCheckInWhenQrPayloadEventIdMismatchesTicketEntity() {
        UUID ticketId = UUID.randomUUID();
        UUID actualEventId = UUID.randomUUID();
        UUID forgedEventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        String jwtQr = "jwt.forged-event.token";
        UUID staffId = UUID.randomUUID();

        vn.eventplatform.ticket.dto.TicketQrPayloadDto payloadDto = vn.eventplatform.ticket.dto.TicketQrPayloadDto.builder()
                .ticketId(ticketId)
                .eventId(forgedEventId)
                .showTimeId(showTimeId)
                .build();

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .qrPayload(jwtQr)
                .eventId(actualEventId)
                .showTimeId(showTimeId)
                .status(TicketStatus.SOLD)
                .build();

        when(ticketQrPayloadService.verifyAndParsePayload(jwtQr)).thenReturn(payloadDto);
        when(ticketRepository.findByIdWithCheckIn(ticketId)).thenReturn(Optional.of(ticket));

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload(jwtQr)
                .staffId(staffId)
                .build();

        assertThatThrownBy(() -> checkInService.processCheckIn(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(appEx.getMessage()).contains("Mã QR không khớp thông tin sự kiện của vé");
                });

        verify(checkInRepository, never()).save(any());
    }

    @Test
    @DisplayName("Từ chối check-in khi mã QR JWT bị can thiệp sai thông tin showTimeId")
    void shouldRejectCheckInWhenQrPayloadShowTimeIdMismatchesTicketEntity() {
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID actualShowTimeId = UUID.randomUUID();
        UUID forgedShowTimeId = UUID.randomUUID();
        String jwtQr = "jwt.forged-showtime.token";
        UUID staffId = UUID.randomUUID();

        vn.eventplatform.ticket.dto.TicketQrPayloadDto payloadDto = vn.eventplatform.ticket.dto.TicketQrPayloadDto.builder()
                .ticketId(ticketId)
                .eventId(eventId)
                .showTimeId(forgedShowTimeId)
                .build();

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .qrPayload(jwtQr)
                .eventId(eventId)
                .showTimeId(actualShowTimeId)
                .status(TicketStatus.SOLD)
                .build();

        when(ticketQrPayloadService.verifyAndParsePayload(jwtQr)).thenReturn(payloadDto);
        when(ticketRepository.findByIdWithCheckIn(ticketId)).thenReturn(Optional.of(ticket));

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload(jwtQr)
                .staffId(staffId)
                .build();

        assertThatThrownBy(() -> checkInService.processCheckIn(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(appEx.getMessage()).contains("Mã QR không khớp thông tin suất diễn của vé");
                });

        verify(checkInRepository, never()).save(any());
    }

    @Test
    @DisplayName("Check-in thành công khi đúng eventId và đúng showTimeId")
    void shouldCheckInSuccessfullyWhenEventAndShowTimeMatch() {
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        String jwtQr = "jwt.valid.token";
        UUID staffId = UUID.randomUUID();

        vn.eventplatform.ticket.dto.TicketQrPayloadDto payloadDto = vn.eventplatform.ticket.dto.TicketQrPayloadDto.builder()
                .ticketId(ticketId)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .ticketTypeName("VIP")
                .build();

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .qrPayload(jwtQr)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .ticketTypeName("VIP")
                .seatNumber("VIP-01")
                .status(TicketStatus.SOLD)
                .build();

        CheckIn savedCheckIn = CheckIn.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .staffId(staffId)
                .method(CheckInMethod.QR_SCAN)
                .checkedInAt(Instant.now())
                .build();

        CheckInResponse expectedResponse = CheckInResponse.builder()
                .id(savedCheckIn.getId())
                .ticketId(ticketId)
                .staffId(staffId)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .ticketTypeName("VIP")
                .seatNumber("VIP-01")
                .method(CheckInMethod.QR_SCAN)
                .build();

        when(ticketQrPayloadService.verifyAndParsePayload(jwtQr)).thenReturn(payloadDto);
        when(ticketRepository.findByIdWithCheckIn(ticketId)).thenReturn(Optional.of(ticket));
        when(checkInRepository.existsByTicketId(ticketId)).thenReturn(false);
        when(checkInMapper.toEntity(ticket, staffId, CheckInMethod.QR_SCAN)).thenReturn(savedCheckIn);
        when(checkInRepository.save(savedCheckIn)).thenReturn(savedCheckIn);
        when(checkInMapper.toResponse(savedCheckIn)).thenReturn(expectedResponse);

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload(jwtQr)
                .staffId(staffId)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .build();

        CheckInResponse response = checkInService.processCheckIn(request);

        assertThat(response).isNotNull();
        assertThat(response.getEventId()).isEqualTo(eventId);
        assertThat(response.getShowTimeId()).isEqualTo(showTimeId);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.USED);
        verify(ticketRepository).save(ticket);
        verify(checkInRepository).save(savedCheckIn);
    }

    @Test
    @DisplayName("validateTicket: Xác thực thông tin vé thành công mà không chuyển trạng thái sang USED")
    void shouldValidateTicketSuccessfullyWithoutChangingStatus() {
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        String jwtQr = "jwt.valid.token";

        vn.eventplatform.ticket.dto.TicketQrPayloadDto payloadDto = vn.eventplatform.ticket.dto.TicketQrPayloadDto.builder()
                .ticketId(ticketId)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .ticketTypeName("STANDARD")
                .build();

        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .qrPayload(jwtQr)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .ticketTypeName("STANDARD")
                .seatNumber("B-05")
                .status(TicketStatus.SOLD)
                .build();

        when(ticketQrPayloadService.verifyAndParsePayload(jwtQr)).thenReturn(payloadDto);
        when(ticketRepository.findByIdWithCheckIn(ticketId)).thenReturn(Optional.of(ticket));
        when(checkInRepository.existsByTicketId(ticketId)).thenReturn(false);

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload(jwtQr)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .build();

        vn.eventplatform.ticket.dto.TicketResponse response = checkInService.validateTicket(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(ticketId);
        assertThat(response.getEventId()).isEqualTo(eventId);
        assertThat(response.getShowTimeId()).isEqualTo(showTimeId);
        assertThat(response.getSeatNumber()).isEqualTo("B-05");
        // Quan trọng: Trạng thái vé không bị đổi sang USED và không lưu vào check_ins
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.SOLD);
        verify(ticketRepository, never()).save(ticket);
        verify(checkInRepository, never()).save(any());
    }
}
