package vn.eventplatform.order.controller;

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
import vn.eventplatform.common.exception.GlobalExceptionHandler;
import vn.eventplatform.order.domain.OrderStatus;
import vn.eventplatform.order.dto.CreateOrderItemRequest;
import vn.eventplatform.order.dto.CreateOrderRequest;
import vn.eventplatform.order.dto.OrderCountdownResponse;
import vn.eventplatform.order.dto.OrderResponse;
import vn.eventplatform.order.security.SecurityConfiguration;
import vn.eventplatform.order.security.TokenVerificationFilter;
import vn.eventplatform.order.service.OrderService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

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
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    @DisplayName("API POST /api/v1/orders tạo order thành công với HTTP 201")
    void shouldCreateOrderSuccessfully() throws Exception {
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        CreateOrderItemRequest itemRequest = CreateOrderItemRequest.builder()
                .ticketTypeId(ticketTypeId)
                .quantity(2)
                .unitPrice(new BigDecimal("250000"))
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .eventId(eventId)
                .showTimeId(showTimeId)
                .customerName("Nguyen Van A")
                .customerEmail("vana@example.com")
                .customerPhone("0987654321")
                .items(List.of(itemRequest))
                .build();

        OrderResponse response = OrderResponse.builder()
                .id(UUID.randomUUID())
                .orderCode("TKZ-20261013-ABCDE")
                .userId(userId)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .subtotal(new BigDecimal("500000"))
                .totalAmount(new BigDecimal("500000"))
                .build();

        when(orderService.createOrder(eq(userId), any(CreateOrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value("TKZ-20261013-ABCDE"))
                .andExpect(jsonPath("$.data.totalAmount").value(500000));
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    @DisplayName("API POST /api/v1/orders trả về lỗi 400 khi thiếu trường bắt buộc")
    void shouldReturnBadRequestWhenMissingFields() throws Exception {
        CreateOrderRequest invalidRequest = CreateOrderRequest.builder()
                .customerEmail("invalid-email")
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    @DisplayName("API GET /api/v1/orders/{id} trả về thông tin đơn hàng")
    void shouldGetOrderByIdSuccessfully() throws Exception {
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID orderId = UUID.randomUUID();

        OrderResponse response = OrderResponse.builder()
                .id(orderId)
                .orderCode("TKZ-20261013-ABCDE")
                .userId(userId)
                .build();

        when(orderService.getOrderById(eq(orderId), eq(userId), eq(false))).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(orderId.toString()))
                .andExpect(jsonPath("$.data.orderCode").value("TKZ-20261013-ABCDE"));
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    @DisplayName("API GET /api/v1/orders/{id}/countdown trả về thông tin đếm ngược thời gian giữ chỗ")
    void shouldGetOrderCountdownByIdSuccessfully() throws Exception {
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(850);

        OrderCountdownResponse response = OrderCountdownResponse.builder()
                .orderId(orderId)
                .orderCode("TKZ-20261017-CD01")
                .status(OrderStatus.PENDING)
                .expiresAt(expiresAt)
                .currentTime(now)
                .remainingSeconds(850L)
                .isExpired(false)
                .build();

        when(orderService.getOrderCountdown(eq(orderId), eq(userId), eq(false))).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/countdown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.orderCode").value("TKZ-20261017-CD01"))
                .andExpect(jsonPath("$.data.remainingSeconds").value(850))
                .andExpect(jsonPath("$.data.isExpired").value(false))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    @DisplayName("API GET /api/v1/orders/code/{orderCode}/countdown trả về thông tin đếm ngược theo mã đơn hàng")
    void shouldGetOrderCountdownByCodeSuccessfully() throws Exception {
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        String orderCode = "TKZ-20261017-CODE99";
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(400);

        OrderCountdownResponse response = OrderCountdownResponse.builder()
                .orderId(UUID.randomUUID())
                .orderCode(orderCode)
                .status(OrderStatus.PENDING)
                .expiresAt(expiresAt)
                .currentTime(now)
                .remainingSeconds(400L)
                .isExpired(false)
                .build();

        when(orderService.getOrderCountdownByCode(eq(orderCode), eq(userId), eq(false))).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/code/" + orderCode + "/countdown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value(orderCode))
                .andExpect(jsonPath("$.data.remainingSeconds").value(400))
                .andExpect(jsonPath("$.data.isExpired").value(false));
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    @DisplayName("API POST /api/v1/orders/{id}/cancel hủy đơn hàng giữ chỗ thành công")
    void shouldCancelOrderSuccessfully() throws Exception {
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID orderId = UUID.randomUUID();

        OrderResponse response = OrderResponse.builder()
                .id(orderId)
                .orderCode("TKZ-20261017-CANCEL")
                .userId(userId)
                .status(OrderStatus.CANCELLED)
                .build();

        when(orderService.cancelOrder(eq(orderId), eq(userId), eq(false))).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Hủy đơn hàng thành công, tồn kho vé đã được giải phóng"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}
