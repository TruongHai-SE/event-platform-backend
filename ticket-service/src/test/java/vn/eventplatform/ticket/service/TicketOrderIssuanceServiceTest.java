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
import vn.eventplatform.ticket.dto.TicketResponse;
import vn.eventplatform.ticket.mapper.TicketMapper;
import vn.eventplatform.ticket.repository.TicketRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketOrderIssuanceServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketMapper ticketMapper;

    private TicketServiceImpl ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketServiceImpl(ticketRepository, ticketMapper);
    }

    @Test
    @DisplayName("1. Phát hành vé thành công khi đơn hàng hợp lệ: sinh đúng số lượng vé và QR payload")
    void issueTicketsForOrder_Success() {
        UUID orderId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID vipTypeId = UUID.randomUUID();
        UUID standardTypeId = UUID.randomUUID();
        String orderCode = "TKZ-20261020-88888";

        List<OrderItemEventPayload> items = List.of(
                OrderItemEventPayload.builder()
                        .ticketTypeId(vipTypeId)
                        .quantity(2)
                        .unitPrice(new BigDecimal("500000"))
                        .build(),
                OrderItemEventPayload.builder()
                        .ticketTypeId(standardTypeId)
                        .quantity(1)
                        .unitPrice(new BigDecimal("200000"))
                        .build()
        );

        when(ticketRepository.existsByOrderId(orderId)).thenReturn(false);
        when(ticketRepository.existsByQrPayload(any())).thenReturn(false);

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

        List<TicketResponse> responses = ticketService.issueTicketsForOrder(orderId, showTimeId, orderCode, items);

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

        // Tất cả vé đều có orderId, showTimeId, status=SOLD và QR bắt đầu bằng TKZ-QR-
        for (Ticket ticket : savedTickets) {
            assertThat(ticket.getOrderId()).isEqualTo(orderId);
            assertThat(ticket.getShowTimeId()).isEqualTo(showTimeId);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.SOLD);
            assertThat(ticket.getQrPayload()).startsWith("TKZ-QR-" + orderCode);
        }
    }

    @Test
    @DisplayName("2. Tính Idempotent: Khi vé của orderId đã tồn tại, không sinh thêm vé mới và trả về vé hiện có")
    void issueTicketsForOrder_Idempotent_WhenAlreadyIssued() {
        UUID orderId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        String orderCode = "TKZ-20261020-99999";

        Ticket existingTicket = Ticket.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .showTimeId(showTimeId)
                .ticketTypeId(UUID.randomUUID())
                .status(TicketStatus.SOLD)
                .qrPayload("TKZ-QR-EXISTING")
                .build();

        when(ticketRepository.existsByOrderId(orderId)).thenReturn(true);
        when(ticketRepository.findByOrderIdWithCheckIn(orderId)).thenReturn(List.of(existingTicket));
        when(ticketMapper.toResponse(existingTicket)).thenReturn(
                TicketResponse.builder()
                        .id(existingTicket.getId())
                        .orderId(orderId)
                        .qrPayload(existingTicket.getQrPayload())
                        .status(TicketStatus.SOLD)
                        .build()
        );

        List<OrderItemEventPayload> items = List.of(
                OrderItemEventPayload.builder()
                        .ticketTypeId(UUID.randomUUID())
                        .quantity(2)
                        .build()
        );

        List<TicketResponse> responses = ticketService.issueTicketsForOrder(orderId, showTimeId, orderCode, items);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getQrPayload()).isEqualTo("TKZ-QR-EXISTING");

        // Đảm bảo không gọi saveAll thêm vé mới
        verify(ticketRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("3. Trả về rỗng an toàn khi orderId là null hoặc items là rỗng")
    void issueTicketsForOrder_EmptyOrNullInputs() {
        List<TicketResponse> r1 = ticketService.issueTicketsForOrder(null, UUID.randomUUID(), "CODE", List.of());
        assertThat(r1).isEmpty();

        List<TicketResponse> r2 = ticketService.issueTicketsForOrder(UUID.randomUUID(), UUID.randomUUID(), "CODE", null);
        assertThat(r2).isEmpty();

        List<TicketResponse> r3 = ticketService.issueTicketsForOrder(UUID.randomUUID(), UUID.randomUUID(), "CODE", List.of());
        assertThat(r3).isEmpty();

        verify(ticketRepository, never()).saveAll(any());
    }
}
