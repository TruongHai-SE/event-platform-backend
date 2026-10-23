package vn.eventplatform.ticket.controller;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.common.exception.GlobalExceptionHandler;
import vn.eventplatform.ticket.domain.TicketStatus;
import vn.eventplatform.ticket.dto.TicketResponse;
import vn.eventplatform.ticket.security.SecurityConfiguration;
import vn.eventplatform.ticket.security.TokenVerificationFilter;
import vn.eventplatform.ticket.service.TicketService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TicketController.class)
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class})
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TicketService ticketService;

    @MockBean
    private TokenVerificationFilter tokenVerificationFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(tokenVerificationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName("GET /api/v1/tickets/{id} - Lấy chi tiết vé thành công khi là chủ sở hữu")
    void testGetTicketById_Success() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        TicketResponse response = TicketResponse.builder()
                .id(ticketId)
                .userId(userId)
                .status(TicketStatus.SOLD)
                .qrPayload("test-qr-payload")
                .ticketTypeName("VIP")
                .build();

        when(ticketService.getTicketDetail(eq(ticketId), eq(userId), anyBoolean())).thenReturn(response);

        mockMvc.perform(get("/api/v1/tickets/{id}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(ticketId.toString()))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.ticketTypeName").value("VIP"))
                .andExpect(jsonPath("$.data.qrPayload").value("test-qr-payload"));
    }

    @Test
    @WithMockUser(username = "22222222-2222-2222-2222-222222222222")
    @DisplayName("GET /api/v1/tickets/{id} - Bị chặn 403 Forbidden khi khách hàng không sở hữu vé")
    void testGetTicketById_Forbidden_WhenNotOwner() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID attackerId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        when(ticketService.getTicketDetail(eq(ticketId), eq(attackerId), anyBoolean()))
                .thenThrow(new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập vé này"));

        mockMvc.perform(get("/api/v1/tickets/{id}", ticketId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập vé này"));
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName("GET /api/v1/tickets/my-tickets - Lấy danh sách ví vé của khách thành công")
    void testGetMyTickets_Success() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        TicketResponse ticket1 = TicketResponse.builder()
                .id(ticketId)
                .userId(userId)
                .eventId(eventId)
                .status(TicketStatus.SOLD)
                .ticketTypeName("Standard")
                .qrImageUrl("/api/v1/tickets/" + ticketId + "/qr-code")
                .build();

        when(ticketService.getMyTickets(eq(userId), any(), any()))
                .thenReturn(new PageImpl<>(List.of(ticket1), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/tickets/my-tickets")
                        .param("status", "SOLD")
                        .param("eventId", eventId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].id").value(ticketId.toString()))
                .andExpect(jsonPath("$.data.items[0].ticketTypeName").value("Standard"))
                .andExpect(jsonPath("$.data.items[0].status").value("SOLD"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName("GET /api/v1/tickets/{id}/qr-code - Tải ảnh mã QR dạng image/png thành công")
    void testGetTicketQrImage_Success() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        byte[] fakePng = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

        when(ticketService.getTicketQrImage(eq(ticketId), eq(300), eq(300), eq(userId), anyBoolean()))
                .thenReturn(fakePng);

        mockMvc.perform(get("/api/v1/tickets/{id}/qr-code", ticketId)
                        .param("width", "300")
                        .param("height", "300"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"ticket-qr-" + ticketId + ".png\""))
                .andExpect(content().bytes(fakePng));
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName("GET /api/v1/tickets/orders/{orderId} - Lấy danh sách vé theo mã đơn hàng")
    void testGetTicketsByOrderId_Success() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        TicketResponse t1 = TicketResponse.builder().id(UUID.randomUUID()).orderId(orderId).userId(userId).build();

        when(ticketService.getTicketsByOrderId(eq(orderId), eq(userId), anyBoolean())).thenReturn(List.of(t1));

        mockMvc.perform(get("/api/v1/tickets/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(t1.getId().toString()));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/tickets - Staff/Admin tìm kiếm vé thành công")
    void testSearchTickets_AdminOrStaff_Success() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        TicketResponse t1 = TicketResponse.builder()
                .id(ticketId)
                .eventId(eventId)
                .ticketTypeName("VIP")
                .status(TicketStatus.SOLD)
                .build();

        when(ticketService.searchTickets(any(), any()))
                .thenReturn(new PageImpl<>(List.of(t1), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/tickets")
                        .param("keyword", "VIP")
                        .param("eventId", eventId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].id").value(ticketId.toString()))
                .andExpect(jsonPath("$.data.items[0].ticketTypeName").value("VIP"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/v1/tickets - Khách hàng thường (role USER) bị chặn 403 khi gọi API quản lý vé")
    void testSearchTickets_Customer_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/tickets")
                        .param("keyword", "VIP"))
                .andExpect(status().isForbidden());
    }
}
