package vn.eventplatform.ticket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.eventplatform.common.messaging.event.OrderItemEventPayload;
import vn.eventplatform.ticket.domain.Ticket;
import vn.eventplatform.ticket.domain.TicketStatus;
import vn.eventplatform.ticket.dto.TicketQrPayloadDto;
import vn.eventplatform.ticket.dto.TicketResponse;
import vn.eventplatform.ticket.mapper.TicketMapper;
import vn.eventplatform.ticket.repository.TicketRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketOrderIssuanceServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private QrCodeGeneratorService qrCodeGeneratorService;

    private TicketQrPayloadService ticketQrPayloadService;
    private TicketServiceImpl ticketService;

    @BeforeEach
    void setUp() {
        ticketQrPayloadService = new TicketQrPayloadServiceImpl(
                "test_dummy_ticket_qr_secret_key_must_be_256_bits_long_abcdef123456"
        );
        ticketService = new TicketServiceImpl(
                ticketRepository,
                ticketMapper,
                ticketQrPayloadService,
                qrCodeGeneratorService
        );
    }

    @Test
    @DisplayName("1. Phát hành vé thành công: sinh đúng số lượng vé và QR payload ký số HMAC-SHA256 chứa đầy đủ eventId, showTimeId, ticketTypeId")
    void issueTicketsForOrder_Success() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID vipTypeId = UUID.randomUUID();
        UUID standardTypeId = UUID.randomUUID();
        String orderCode = "TKZ-20261020-88888";

        List<OrderItemEventPayload> items = List.of(
                OrderItemEventPayload.builder()
                        .ticketTypeId(vipTypeId)
                        .ticketTypeName("VIP")
                        .quantity(2)
                        .unitPrice(new BigDecimal("500000"))
                        .build(),
                OrderItemEventPayload.builder()
                        .ticketTypeId(standardTypeId)
                        .ticketTypeName("Standard")
                        .quantity(1)
                        .unitPrice(new BigDecimal("200000"))
                        .build()
        );

        when(ticketRepository.existsByOrderId(orderId)).thenReturn(false);
        when(ticketRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketMapper.toResponse(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            return TicketResponse.builder()
                    .id(t.getId() != null ? t.getId() : UUID.randomUUID())
                    .orderId(t.getOrderId())
                    .showTimeId(t.getShowTimeId())
                    .ticketTypeId(t.getTicketTypeId())
                    .qrPayload(t.getQrPayload())
                    .status(t.getStatus())
                    .build();
        });

        List<TicketResponse> responses = ticketService.issueTicketsForOrder(orderId, eventId, showTimeId, orderCode, items);

        assertThat(responses).hasSize(3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ticket>> captor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(captor.capture());

        List<Ticket> savedTickets = captor.getValue();
        assertThat(savedTickets).hasSize(3);

        // Kiểm tra 2 vé VIP
        List<Ticket> vipTickets = savedTickets.stream()
                .filter(t -> t.getTicketTypeId().equals(vipTypeId))
                .toList();
        assertThat(vipTickets).hasSize(2);

        // Kiểm tra 1 vé Standard
        List<Ticket> stdTickets = savedTickets.stream()
                .filter(t -> t.getTicketTypeId().equals(standardTypeId))
                .toList();
        assertThat(stdTickets).hasSize(1);

        // Tất cả vé đều có QR payload JWT hợp lệ có thể verify và chứa đủ claim
        for (Ticket ticket : savedTickets) {
            assertThat(ticket.getId()).isNotNull();
            assertThat(ticket.getOrderId()).isEqualTo(orderId);
            assertThat(ticket.getShowTimeId()).isEqualTo(showTimeId);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.SOLD);
            assertThat(ticket.getQrPayload()).isNotBlank();

            TicketQrPayloadDto payload = ticketQrPayloadService.verifyAndParsePayload(ticket.getQrPayload());
            assertThat(payload.getTicketId()).isEqualTo(ticket.getId());
            assertThat(payload.getEventId()).isEqualTo(eventId);
            assertThat(payload.getShowTimeId()).isEqualTo(showTimeId);
            assertThat(payload.getOrderId()).isEqualTo(orderId);
            assertThat(payload.getTicketTypeId()).isEqualTo(ticket.getTicketTypeId());
            assertThat(payload.getTicketTypeName()).isIn("VIP", "Standard");
        }
    }

    @Test
    @DisplayName("2. Tính Idempotent: Khi vé của orderId đã tồn tại thì không tạo thêm và trả về danh sách hiện có")
    void issueTicketsForOrder_Idempotent_WhenAlreadyIssued() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        String orderCode = "TKZ-20261020-99999";

        List<OrderItemEventPayload> items = List.of(
                OrderItemEventPayload.builder()
                        .ticketTypeId(UUID.randomUUID())
                        .quantity(1)
                        .build()
        );

        Ticket existingTicket = Ticket.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .showTimeId(showTimeId)
                .status(TicketStatus.SOLD)
                .qrPayload("EXISTING_QR")
                .build();

        when(ticketRepository.existsByOrderId(orderId)).thenReturn(true);
        when(ticketRepository.findByOrderIdWithCheckIn(orderId)).thenReturn(List.of(existingTicket));
        when(ticketMapper.toResponse(existingTicket)).thenReturn(
                TicketResponse.builder()
                        .id(existingTicket.getId())
                        .orderId(orderId)
                        .status(TicketStatus.SOLD)
                        .qrPayload("EXISTING_QR")
                        .build()
        );

        List<TicketResponse> responses = ticketService.issueTicketsForOrder(orderId, eventId, showTimeId, orderCode, items);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getQrPayload()).isEqualTo("EXISTING_QR");
        verify(ticketRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("3. Trả về rỗng khi orderId null hoặc items rỗng")
    void issueTicketsForOrder_EmptyOrNullInputs() {
        List<TicketResponse> r1 = ticketService.issueTicketsForOrder(null, UUID.randomUUID(), UUID.randomUUID(), "CODE", List.of());
        assertThat(r1).isEmpty();

        List<TicketResponse> r2 = ticketService.issueTicketsForOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CODE", null);
        assertThat(r2).isEmpty();

        List<TicketResponse> r3 = ticketService.issueTicketsForOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CODE", List.of());
        assertThat(r3).isEmpty();

        verify(ticketRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("4. Lấy ảnh QR của vé thành công")
    void testGetTicketQrImage_Success() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .qrPayload("QR_TOKEN_STRING")
                .status(TicketStatus.SOLD)
                .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(qrCodeGeneratorService.generateQrPng("QR_TOKEN_STRING", 300, 300))
                .thenReturn(new byte[]{1, 2, 3});

        byte[] result = ticketService.getTicketQrImage(ticketId, 300, 300);
        assertThat(result).containsExactly(1, 2, 3);
    }
}
