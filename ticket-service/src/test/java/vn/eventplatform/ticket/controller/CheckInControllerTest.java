package vn.eventplatform.ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.common.exception.GlobalExceptionHandler;
import vn.eventplatform.ticket.domain.CheckInMethod;
import vn.eventplatform.ticket.domain.TicketStatus;
import vn.eventplatform.ticket.dto.CheckInRequest;
import vn.eventplatform.ticket.dto.CheckInResponse;
import vn.eventplatform.ticket.dto.TicketResponse;
import vn.eventplatform.ticket.security.SecurityConfiguration;
import vn.eventplatform.ticket.security.TokenVerificationFilter;
import vn.eventplatform.ticket.service.CheckInService;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CheckInController.class)
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class})
class CheckInControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CheckInService checkInService;

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
    @WithMockUser(roles = "STAFF")
    @DisplayName("POST /api/v1/check-ins - Staff check-in thành công khi đúng sự kiện và suất diễn")
    void testCheckIn_Staff_Success() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload("valid.qr.payload")
                .staffId(staffId)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .build();

        CheckInResponse response = CheckInResponse.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .staffId(staffId)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .ticketTypeName("VIP")
                .seatNumber("A-01")
                .method(CheckInMethod.QR_SCAN)
                .checkedInAt(Instant.now())
                .build();

        when(checkInService.processCheckIn(any(CheckInRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/check-ins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.data.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.data.showTimeId").value(showTimeId.toString()))
                .andExpect(jsonPath("$.data.ticketTypeName").value("VIP"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("POST /api/v1/check-ins - Từ chối check-in khi sai sự kiện (400 Bad Request)")
    void testCheckIn_WrongEvent_BadRequest() throws Exception {
        UUID staffId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload("qr-wrong-event")
                .staffId(staffId)
                .eventId(eventId)
                .build();

        when(checkInService.processCheckIn(any(CheckInRequest.class)))
                .thenThrow(new AppException(ErrorCode.BAD_REQUEST, "Vé không thuộc sự kiện này"));

        mockMvc.perform(post("/api/v1/check-ins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Vé không thuộc sự kiện này"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("POST /api/v1/check-ins - Từ chối check-in khi sai suất diễn (400 Bad Request)")
    void testCheckIn_WrongShowTime_BadRequest() throws Exception {
        UUID staffId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload("qr-wrong-showtime")
                .staffId(staffId)
                .showTimeId(showTimeId)
                .build();

        when(checkInService.processCheckIn(any(CheckInRequest.class)))
                .thenThrow(new AppException(ErrorCode.BAD_REQUEST, "Vé không thuộc suất diễn này"));

        mockMvc.perform(post("/api/v1/check-ins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Vé không thuộc suất diễn này"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST /api/v1/check-ins - Khách hàng thường (role USER) bị chặn 403 Forbidden")
    void testCheckIn_Customer_Forbidden() throws Exception {
        CheckInRequest request = CheckInRequest.builder()
                .qrPayload("qr-payload")
                .staffId(UUID.randomUUID())
                .build();

        mockMvc.perform(post("/api/v1/check-ins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("POST /api/v1/check-ins/validate - Xác thực vé thành công trước khi check-in")
    void testValidateTicket_Success() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload("qr-preview")
                .eventId(eventId)
                .showTimeId(showTimeId)
                .build();

        TicketResponse ticketResponse = TicketResponse.builder()
                .id(ticketId)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .ticketTypeName("STANDARD")
                .seatNumber("C-12")
                .status(TicketStatus.SOLD)
                .build();

        when(checkInService.validateTicket(any(CheckInRequest.class))).thenReturn(ticketResponse);

        mockMvc.perform(post("/api/v1/check-ins/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(ticketId.toString()))
                .andExpect(jsonPath("$.data.seatNumber").value("C-12"))
                .andExpect(jsonPath("$.data.status").value("SOLD"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/check-ins/tickets/{ticketId} - Lấy thông tin check-in theo ID vé")
    void testGetCheckInByTicketId_Success() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        CheckInResponse response = CheckInResponse.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .staffId(staffId)
                .seatNumber("VIP-01")
                .method(CheckInMethod.QR_SCAN)
                .checkedInAt(Instant.now())
                .build();

        when(checkInService.getCheckInByTicketId(ticketId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/check-ins/tickets/{ticketId}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.data.staffId").value(staffId.toString()));
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "ORGANIZER")
    @DisplayName("POST /api/v1/check-ins - Ban tổ chức (ORGANIZER) có quyền scanner và tự động gán staffId từ token")
    void testCheckIn_Organizer_Success_And_AutoResolvesStaffId() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID organizerId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        CheckInRequest request = CheckInRequest.builder()
                .qrPayload("organizer.scan.qr")
                .build();

        CheckInResponse response = CheckInResponse.builder()
                .id(UUID.randomUUID())
                .ticketId(ticketId)
                .staffId(organizerId)
                .seatNumber("VIP-88")
                .method(CheckInMethod.QR_SCAN)
                .checkedInAt(Instant.now())
                .build();

        when(checkInService.processCheckIn(any(CheckInRequest.class))).thenAnswer(invocation -> {
            CheckInRequest req = invocation.getArgument(0);
            if (organizerId.equals(req.getStaffId())) {
                return response;
            }
            throw new IllegalArgumentException("staffId không được resolve đúng");
        });

        mockMvc.perform(post("/api/v1/check-ins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.data.staffId").value(organizerId.toString()));
    }
}
