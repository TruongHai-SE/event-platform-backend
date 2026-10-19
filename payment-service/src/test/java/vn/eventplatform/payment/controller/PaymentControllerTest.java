package vn.eventplatform.payment.controller;

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
import vn.eventplatform.payment.domain.PaymentGateway;
import vn.eventplatform.payment.domain.PaymentStatus;
import vn.eventplatform.payment.domain.RefundStatus;
import vn.eventplatform.payment.dto.*;
import vn.eventplatform.payment.dto.sepay.SepayWebhookRequest;
import vn.eventplatform.payment.dto.sepay.SepayWebhookResponse;
import vn.eventplatform.payment.payment.dto.PaymentCallbackResult;
import vn.eventplatform.payment.security.SecurityConfiguration;
import vn.eventplatform.payment.security.TokenVerificationFilter;
import vn.eventplatform.payment.service.PaymentService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(controllers = PaymentController.class)
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private TokenVerificationFilter tokenVerificationFilter;

    private static final String TEST_USER_ID = "11111111-1111-1111-1111-111111111111";

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(tokenVerificationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(username = TEST_USER_ID, roles = {"USER"})
    @DisplayName("POST /api/v1/payments - Khởi tạo thanh toán thành công với orderCode")
    void createPayment_Authenticated_Success_WithOrderCode() throws Exception {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderCode("TKZ-20261019-001")
                .method(PaymentGateway.SEPAY)
                .build();

        PaymentInitResponse response = PaymentInitResponse.builder()
                .paymentId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .orderCode("TKZ-20261019-001")
                .method(PaymentGateway.SEPAY)
                .amount(new BigDecimal("150000.00"))
                .paymentUrl("https://qr.sepay.vn/img?des=TKZ20261019001")
                .qrCode("https://qr.sepay.vn/img?des=TKZ20261019001")
                .status(PaymentStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq(UUID.fromString(TEST_USER_ID)), eq(false), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/payments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value("TKZ-20261019-001"))
                .andExpect(jsonPath("$.data.method").value("SEPAY"))
                .andExpect(jsonPath("$.data.qrCode").value("https://qr.sepay.vn/img?des=TKZ20261019001"));
    }

    @Test
    @DisplayName("POST /api/v1/payments - Chưa đăng nhập trả về 403 Forbidden")
    void createPayment_Unauthenticated_Fails() throws Exception {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderCode("TKZ-20261019-001")
                .method(PaymentGateway.VNPAY)
                .build();

        mockMvc.perform(post("/api/v1/payments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_USER_ID, roles = {"USER"})
    @DisplayName("POST /api/v1/payments - Thiếu cả orderCode và orderId trả về 400 Bad Request")
    void createPayment_MissingOrderIdentifier_Fails() throws Exception {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .method(PaymentGateway.VNPAY)
                .build();

        mockMvc.perform(post("/api/v1/payments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = TEST_USER_ID, roles = {"USER"})
    @DisplayName("GET /api/v1/payments/{id} - Lấy chi tiết thanh toán thành công")
    void getPaymentById_Authenticated_Success() throws Exception {
        UUID paymentId = UUID.randomUUID();
        PaymentResponse response = PaymentResponse.builder()
                .id(paymentId)
                .orderCode("TKZ-20261019-001")
                .method(PaymentGateway.VNPAY)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentService.getPaymentById(eq(paymentId), eq(UUID.fromString(TEST_USER_ID)), eq(false)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value("TKZ-20261019-001"));
    }

    @Test
    @WithMockUser(username = TEST_USER_ID, roles = {"USER"})
    @DisplayName("GET /api/v1/payments/order-code/{orderCode} - Lấy danh sách giao dịch theo orderCode")
    void getPaymentsByOrderCode_Authenticated_Success() throws Exception {
        String orderCode = "TKZ-20261019-001";
        PaymentResponse payment = PaymentResponse.builder()
                .id(UUID.randomUUID())
                .orderCode(orderCode)
                .method(PaymentGateway.SEPAY)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentService.getPaymentsByOrderCode(eq(orderCode), eq(UUID.fromString(TEST_USER_ID)), eq(false)))
                .thenReturn(List.of(payment));

        mockMvc.perform(get("/api/v1/payments/order-code/{orderCode}", orderCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].orderCode").value(orderCode));
    }

    @Test
    @DisplayName("GET /api/v1/payments/vnpay/callback - Endpoint public xử lý callback VNPay")
    void handleVnPayCallback_Public_Success() throws Exception {
        PaymentCallbackResult callbackResult = PaymentCallbackResult.builder()
                .success(true)
                .orderCode("TKZ-20261019-001")
                .transactionId("14000123")
                .amount(new BigDecimal("100000.00"))
                .message("Thanh toán thành công")
                .build();

        when(paymentService.handleVnPayCallback(any())).thenReturn(callbackResult);

        mockMvc.perform(get("/api/v1/payments/vnpay/callback")
                        .param("vnp_TxnRef", "TKZ-20261019-001")
                        .param("vnp_ResponseCode", "00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value("TKZ-20261019-001"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/sepay/webhook - Endpoint public nhận webhook SePay")
    void handleSepayWebhook_Public_Success() throws Exception {
        SepayWebhookRequest webhookRequest = SepayWebhookRequest.builder()
                .id(12345L)
                .code("TKZ20261019001")
                .transferAmount(new BigDecimal("100000.00"))
                .build();

        SepayWebhookResponse response = SepayWebhookResponse.builder()
                .success(true)
                .message("Xử lý thanh toán thành công")
                .build();

        when(paymentService.handleSepayWebhook(any(), any(SepayWebhookRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/sepay/webhook")
                        .with(csrf())
                        .header("Authorization", "Apikey test_key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhookRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("POST /api/v1/payments/refund - Admin có quyền tạo yêu cầu hoàn tiền")
    void createRefund_Admin_Success() throws Exception {
        CreateRefundRequest request = CreateRefundRequest.builder()
                .paymentId(UUID.randomUUID())
                .amount(new BigDecimal("50000.00"))
                .reason("Event cancelled")
                .idempotencyKey("IDEM-REFUND-001")
                .build();

        RefundResponse response = RefundResponse.builder()
                .id(UUID.randomUUID())
                .paymentId(request.getPaymentId())
                .amount(request.getAmount())
                .status(RefundStatus.PROCESSING)
                .build();

        when(paymentService.createRefund(any(CreateRefundRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/refund")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    @DisplayName("POST /api/v1/payments/refund - User thường bị từ chối 403 Forbidden khi tạo hoàn tiền")
    void createRefund_RegularUser_Forbidden() throws Exception {
        CreateRefundRequest request = CreateRefundRequest.builder()
                .paymentId(UUID.randomUUID())
                .amount(new BigDecimal("50000.00"))
                .reason("Event cancelled")
                .idempotencyKey("IDEM-REFUND-001")
                .build();

        mockMvc.perform(post("/api/v1/payments/refund")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
