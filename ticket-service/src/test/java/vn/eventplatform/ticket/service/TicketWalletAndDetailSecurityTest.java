package vn.eventplatform.ticket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.ticket.client.OrderServiceClient;
import vn.eventplatform.ticket.client.dto.InternalOrderResponse;
import vn.eventplatform.ticket.domain.Ticket;
import vn.eventplatform.ticket.domain.TicketStatus;
import vn.eventplatform.ticket.dto.TicketFilterRequest;
import vn.eventplatform.ticket.dto.TicketResponse;
import vn.eventplatform.ticket.mapper.CheckInMapperImpl;
import vn.eventplatform.ticket.mapper.TicketMapper;
import vn.eventplatform.ticket.mapper.TicketMapperImpl;
import vn.eventplatform.ticket.repository.TicketRepository;

import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketWalletAndDetailSecurityTest {

    @Mock
    private TicketRepository ticketRepository;

    @Spy
    private TicketMapper ticketMapper = new TicketMapperImpl();

    @Mock
    private TicketQrPayloadService ticketQrPayloadService;

    @Mock
    private QrCodeGeneratorService qrCodeGeneratorService;

    @Mock
    private OrderServiceClient orderServiceClient;

    @Mock
    private vn.eventplatform.ticket.repository.CheckInRepository checkInRepository;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private final UUID ownerUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID attackerUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID adminUserId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID ticketId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ticketMapper, "checkInMapper", new CheckInMapperImpl());
        ReflectionTestUtils.setField(ticketService, "orderServiceClient", orderServiceClient);
        ReflectionTestUtils.setField(ticketService, "checkInRepository", checkInRepository);
        ReflectionTestUtils.setField(ticketService, "internalSecret", "test-internal-secret");
    }

    @Test
    @DisplayName("1. getMyTickets: Lấy danh sách ví vé của khách thành công có phân trang")
    void testGetMyTickets_Success() {
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .userId(ownerUserId)
                .eventId(eventId)
                .ticketTypeName("VIP")
                .status(TicketStatus.SOLD)
                .qrPayload("test-qr")
                .build();

        Page<Ticket> pageResult = new PageImpl<>(List.of(ticket), PageRequest.of(0, 10), 1);
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

        TicketFilterRequest filter = TicketFilterRequest.builder()
                .status(TicketStatus.SOLD)
                .eventId(eventId)
                .build();

        Page<TicketResponse> responses = ticketService.getMyTickets(ownerUserId, filter, PageRequest.of(0, 10));

        assertThat(responses).isNotEmpty();
        assertThat(responses.getTotalElements()).isEqualTo(1);
        TicketResponse resp = responses.getContent().getFirst();
        assertThat(resp.getId()).isEqualTo(ticketId);
        assertThat(resp.getUserId()).isEqualTo(ownerUserId);
        assertThat(resp.getTicketTypeName()).isEqualTo("VIP");
        assertThat(resp.getQrImageUrl()).isEqualTo("/api/v1/tickets/" + ticketId + "/qr-code");
    }

    @Test
    @DisplayName("2. getMyTickets: Ném lỗi UNAUTHORIZED khi userId là null")
    void testGetMyTickets_ThrowsUnauthorized_WhenUserIdNull() {
        TicketFilterRequest filter = TicketFilterRequest.builder().build();
        assertThatThrownBy(() -> ticketService.getMyTickets(null, filter, PageRequest.of(0, 10)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("3. getTicketDetail: Chủ sở hữu vé xem chi tiết thành công")
    void testGetTicketDetail_OwnerAccess_Success() {
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .userId(ownerUserId)
                .eventId(eventId)
                .ticketTypeName("Standard")
                .status(TicketStatus.SOLD)
                .qrPayload("test-qr")
                .build();

        when(ticketRepository.findByIdWithCheckIn(ticketId)).thenReturn(Optional.of(ticket));

        TicketResponse response = ticketService.getTicketDetail(ticketId, ownerUserId, false);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(ticketId);
        assertThat(response.getUserId()).isEqualTo(ownerUserId);
    }

    @Test
    @DisplayName("4. getTicketDetail: Bị chặn FORBIDDEN khi người dùng khác cố tình xem vé")
    void testGetTicketDetail_AttackerAccess_ThrowsForbidden() {
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .userId(ownerUserId)
                .eventId(eventId)
                .ticketTypeName("Standard")
                .status(TicketStatus.SOLD)
                .build();

        when(ticketRepository.findByIdWithCheckIn(ticketId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.getTicketDetail(ticketId, attackerUserId, false))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("5. getTicketDetail: Admin hoặc Staff được quyền xem chi tiết vé bất kỳ")
    void testGetTicketDetail_AdminAccess_Success() {
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .userId(ownerUserId)
                .eventId(eventId)
                .ticketTypeName("Standard")
                .status(TicketStatus.SOLD)
                .build();

        when(ticketRepository.findByIdWithCheckIn(ticketId)).thenReturn(Optional.of(ticket));

        TicketResponse response = ticketService.getTicketDetail(ticketId, adminUserId, true);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(ticketId);
    }

    @Test
    @DisplayName("6. getTicketDetail: Fallback gọi order-service xác minh quyền sở hữu cho vé cũ chưa có userId")
    void testGetTicketDetail_LegacyTicket_FallbackOrderService_Success() {
        Ticket legacyTicket = Ticket.builder()
                .id(ticketId)
                .userId(null)
                .orderId(orderId)
                .eventId(eventId)
                .status(TicketStatus.SOLD)
                .build();

        when(ticketRepository.findByIdWithCheckIn(ticketId)).thenReturn(Optional.of(legacyTicket));

        InternalOrderResponse orderData = InternalOrderResponse.builder()
                .id(orderId)
                .userId(ownerUserId)
                .eventId(eventId)
                .build();

        when(orderServiceClient.getOrderById(anyString(), eq(orderId)))
                .thenReturn(ApiResponse.ok(orderData));

        TicketResponse response = ticketService.getTicketDetail(ticketId, ownerUserId, false);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(ticketId);
        verify(ticketRepository).save(legacyTicket);
        assertThat(legacyTicket.getUserId()).isEqualTo(ownerUserId);
    }

    @Test
    @DisplayName("7. getTicketDetail: Bị chặn FORBIDDEN khi vé cũ fallback sang order-service nhưng không khớp chủ sở hữu")
    void testGetTicketDetail_LegacyTicket_FallbackOrderService_MismatchOwner_ThrowsForbidden() {
        Ticket legacyTicket = Ticket.builder()
                .id(ticketId)
                .userId(null)
                .orderId(orderId)
                .eventId(eventId)
                .status(TicketStatus.SOLD)
                .build();

        when(ticketRepository.findByIdWithCheckIn(ticketId)).thenReturn(Optional.of(legacyTicket));

        InternalOrderResponse orderData = InternalOrderResponse.builder()
                .id(orderId)
                .userId(ownerUserId) // Đơn hàng của ownerUserId
                .eventId(eventId)
                .build();

        when(orderServiceClient.getOrderById(anyString(), eq(orderId)))
                .thenReturn(ApiResponse.ok(orderData));

        // attacker cố tình xem
        assertThatThrownBy(() -> ticketService.getTicketDetail(ticketId, attackerUserId, false))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("8. getTicketQrImage: Khách hàng chỉ tải được ảnh QR của vé chính mình")
    void testGetTicketQrImage_OwnerAccess_Success() {
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .userId(ownerUserId)
                .qrPayload("valid-qr-jwt")
                .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(qrCodeGeneratorService.generateQrPng("valid-qr-jwt", 300, 300))
                .thenReturn(new byte[]{1, 2, 3});

        byte[] qrBytes = ticketService.getTicketQrImage(ticketId, 300, 300, ownerUserId, false);

        assertThat(qrBytes).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("9. getTicketQrImage: Khách khác tải QR của vé không thuộc về mình bị chặn 403 Forbidden")
    void testGetTicketQrImage_AttackerAccess_ThrowsForbidden() {
        Ticket ticket = Ticket.builder()
                .id(ticketId)
                .userId(ownerUserId)
                .qrPayload("valid-qr-jwt")
                .build();

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.getTicketQrImage(ticketId, 300, 300, attackerUserId, false))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(qrCodeGeneratorService, never()).generateQrPng(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("10. getTicketsByOrderId: Bảo mật danh sách vé theo đơn hàng")
    void testGetTicketsByOrderId_Security() {
        Ticket t1 = Ticket.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(ownerUserId)
                .build();

        when(ticketRepository.findByOrderIdWithCheckIn(orderId)).thenReturn(List.of(t1));

        // Chủ đơn hàng xem thành công
        List<TicketResponse> ownerList = ticketService.getTicketsByOrderId(orderId, ownerUserId, false);
        assertThat(ownerList).hasSize(1);

        // Kẻ tấn công bị ném FORBIDDEN
        assertThatThrownBy(() -> ticketService.getTicketsByOrderId(orderId, attackerUserId, false))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        // Admin xem thành công
        List<TicketResponse> adminList = ticketService.getTicketsByOrderId(orderId, adminUserId, true);
        assertThat(adminList).hasSize(1);
    }

    @Test
    @DisplayName("11. searchTickets: Admin/Staff tìm kiếm và lọc danh sách vé thành công")
    void testSearchTickets_Success() {
        Ticket t1 = Ticket.builder()
                .id(ticketId)
                .eventId(eventId)
                .ticketTypeName("VIP")
                .seatNumber("A-01")
                .status(TicketStatus.SOLD)
                .build();

        Page<Ticket> pageResult = new PageImpl<>(List.of(t1), PageRequest.of(0, 10), 1);
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pageResult);

        TicketFilterRequest filter = TicketFilterRequest.builder()
                .keyword("VIP")
                .eventId(eventId)
                .status(TicketStatus.SOLD)
                .build();

        Page<TicketResponse> responses = ticketService.searchTickets(filter, PageRequest.of(0, 10));

        assertThat(responses).isNotEmpty();
        assertThat(responses.getTotalElements()).isEqualTo(1);
        assertThat(responses.getContent().getFirst().getTicketTypeName()).isEqualTo("VIP");
        assertThat(responses.getContent().getFirst().getSeatNumber()).isEqualTo("A-01");
    }

    @Test
    @DisplayName("12. getTicketStatistics: Thống kê realtime vé đã bán, đã check-in, đã hủy và tỷ lệ check-in")
    void testGetTicketStatistics_Success() {
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        vn.eventplatform.ticket.repository.projection.StatusCountProjection soldProj =
                new vn.eventplatform.ticket.repository.projection.StatusCountProjection() {
                    @Override
                    public TicketStatus getStatus() { return TicketStatus.SOLD; }
                    @Override
                    public Long getCount() { return 20L; }
                };
        vn.eventplatform.ticket.repository.projection.StatusCountProjection usedProj =
                new vn.eventplatform.ticket.repository.projection.StatusCountProjection() {
                    @Override
                    public TicketStatus getStatus() { return TicketStatus.USED; }
                    @Override
                    public Long getCount() { return 80L; }
                };
        vn.eventplatform.ticket.repository.projection.StatusCountProjection cancelledProj =
                new vn.eventplatform.ticket.repository.projection.StatusCountProjection() {
                    @Override
                    public TicketStatus getStatus() { return TicketStatus.CANCELLED; }
                    @Override
                    public Long getCount() { return 5L; }
                };

        when(ticketRepository.countByStatus(eventId, showTimeId))
                .thenReturn(List.of(soldProj, usedProj, cancelledProj));

        vn.eventplatform.ticket.repository.projection.TicketTypeStatusCountProjection typeProj =
                new vn.eventplatform.ticket.repository.projection.TicketTypeStatusCountProjection() {
                    @Override
                    public UUID getTicketTypeId() { return ticketTypeId; }
                    @Override
                    public String getTicketTypeName() { return "VIP Diamond"; }
                    @Override
                    public TicketStatus getStatus() { return TicketStatus.USED; }
                    @Override
                    public Long getCount() { return 80L; }
                };

        when(ticketRepository.countByTicketTypeAndStatus(eventId, showTimeId))
                .thenReturn(List.of(typeProj));

        vn.eventplatform.ticket.repository.projection.MethodCountProjection methodProj =
                new vn.eventplatform.ticket.repository.projection.MethodCountProjection() {
                    @Override
                    public vn.eventplatform.ticket.domain.CheckInMethod getMethod() {
                        return vn.eventplatform.ticket.domain.CheckInMethod.QR_SCAN;
                    }
                    @Override
                    public Long getCount() { return 80L; }
                };

        Instant now = Instant.now();
        when(checkInRepository.countByMethod(eventId, showTimeId)).thenReturn(List.of(methodProj));
        when(checkInRepository.findLastCheckedInAt(eventId, showTimeId)).thenReturn(Optional.of(now));

        vn.eventplatform.ticket.dto.TicketStatisticsResponse stats =
                ticketService.getTicketStatistics(eventId, showTimeId);

        assertThat(stats).isNotNull();
        assertThat(stats.getEventId()).isEqualTo(eventId);
        assertThat(stats.getShowTimeId()).isEqualTo(showTimeId);
        assertThat(stats.getTotalIssued()).isEqualTo(105L);
        assertThat(stats.getTotalSold()).isEqualTo(20L);
        assertThat(stats.getTotalCheckedIn()).isEqualTo(80L);
        assertThat(stats.getTotalCancelled()).isEqualTo(5L);
        // checkInRate = 80 / (20 + 80) * 100 = 80.0%
        assertThat(stats.getCheckInRate()).isEqualTo(80.0);
        assertThat(stats.getLastCheckedInAt()).isEqualTo(now);
        assertThat(stats.getCheckInByMethod().get(vn.eventplatform.ticket.domain.CheckInMethod.QR_SCAN)).isEqualTo(80L);
        assertThat(stats.getByTicketType()).hasSize(1);
        assertThat(stats.getByTicketType().getFirst().getTicketTypeName()).isEqualTo("VIP Diamond");
        assertThat(stats.getByTicketType().getFirst().getTotalCheckedIn()).isEqualTo(80L);
    }
}
