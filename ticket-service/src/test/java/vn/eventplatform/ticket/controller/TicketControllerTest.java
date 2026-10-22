package vn.eventplatform.ticket.controller;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import vn.eventplatform.common.exception.GlobalExceptionHandler;
import vn.eventplatform.ticket.domain.TicketStatus;
import vn.eventplatform.ticket.dto.TicketResponse;
import vn.eventplatform.ticket.security.SecurityConfiguration;
import vn.eventplatform.ticket.security.TokenVerificationFilter;
import vn.eventplatform.ticket.service.TicketService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @WithMockUser
    @DisplayName("GET /api/v1/tickets/{id} - Lấy chi tiết vé thành công")
    void testGetTicketById_Success() throws Exception {
        UUID ticketId = UUID.randomUUID();
        TicketResponse response = TicketResponse.builder()
                .id(ticketId)
                .status(TicketStatus.SOLD)
                .qrPayload("test-qr-payload")
                .build();

        when(ticketService.getTicketById(ticketId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/tickets/{id}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(ticketId.toString()))
                .andExpect(jsonPath("$.data.qrPayload").value("test-qr-payload"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/tickets/{id}/qr-code - Tải ảnh mã QR dạng image/png thành công")
    void testGetTicketQrImage_Success() throws Exception {
        UUID ticketId = UUID.randomUUID();
        byte[] fakePng = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

        when(ticketService.getTicketQrImage(eq(ticketId), eq(300), eq(300))).thenReturn(fakePng);

        mockMvc.perform(get("/api/v1/tickets/{id}/qr-code", ticketId)
                        .param("width", "300")
                        .param("height", "300"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"ticket-qr-" + ticketId + ".png\""))
                .andExpect(content().bytes(fakePng));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/tickets/orders/{orderId} - Lấy danh sách vé theo mã đơn hàng")
    void testGetTicketsByOrderId_Success() throws Exception {
        UUID orderId = UUID.randomUUID();
        TicketResponse t1 = TicketResponse.builder().id(UUID.randomUUID()).orderId(orderId).build();

        when(ticketService.getTicketsByOrderId(orderId)).thenReturn(List.of(t1));

        mockMvc.perform(get("/api/v1/tickets/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(t1.getId().toString()));
    }
}
