package vn.eventplatform.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.eventplatform.common.exception.GlobalExceptionHandler;
import vn.eventplatform.payment.domain.RefundStatus;
import vn.eventplatform.payment.dto.*;
import vn.eventplatform.payment.security.SecurityConfiguration;
import vn.eventplatform.payment.security.TokenVerificationFilter;
import vn.eventplatform.payment.service.PaymentService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalPaymentController.class)
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "security.internal.secret=test-internal-secret-key"
})
class InternalPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private TokenVerificationFilter tokenVerificationFilter;

    private static final String SECRET_HEADER = "X-Internal-Secret";
    private static final String VALID_SECRET = "test-internal-secret-key";

    @Test
    @DisplayName("POST /internal/v1/payments/refunds/batch - Thành công với valid internal secret")
    void batchRefundByOrders_ValidSecret_Success() throws Exception {
        UUID orderId = UUID.randomUUID();
        BatchRefundByOrdersRequest request = BatchRefundByOrdersRequest.builder()
                .requests(List.of(
                        InternalRefundOrderRequest.builder()
                                .orderId(orderId)
                                .amount(new BigDecimal("200000.00"))
                                .reason("Show cancelled")
                                .idempotencyKey("IDEM-BATCH-ORDER-001")
                                .build()
                ))
                .chunkSize(20)
                .requestsPerSecond(10.0)
                .build();

        BatchRefundResponse response = BatchRefundResponse.builder()
                .totalRequested(1)
                .totalSuccess(1)
                .totalFailed(0)
                .totalRefundedAmount(new BigDecimal("200000.00"))
                .chunkSize(20)
                .requestsPerSecond(10.0)
                .results(List.of(
                        BatchRefundItemResult.builder()
                                .refundLogId(UUID.randomUUID())
                                .orderId(orderId)
                                .amount(new BigDecimal("200000.00"))
                                .status(RefundStatus.SUCCESS)
                                .message("Xử lý hoàn tiền thành công")
                                .build()
                ))
                .build();

        when(paymentService.batchRefundByOrders(any(BatchRefundByOrdersRequest.class))).thenReturn(response);

        mockMvc.perform(post("/internal/v1/payments/refunds/batch")
                        .header(SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRequested").value(1))
                .andExpect(jsonPath("$.data.totalSuccess").value(1))
                .andExpect(jsonPath("$.data.totalFailed").value(0))
                .andExpect(jsonPath("$.data.totalRefundedAmount").value(200000.00));
    }

    @Test
    @DisplayName("POST /internal/v1/payments/refunds/batch - Từ chối 403 Forbidden khi sai secret")
    void batchRefundByOrders_InvalidSecret_Forbidden() throws Exception {
        BatchRefundByOrdersRequest request = BatchRefundByOrdersRequest.builder()
                .requests(List.of(
                        InternalRefundOrderRequest.builder()
                                .orderId(UUID.randomUUID())
                                .amount(new BigDecimal("100000.00"))
                                .reason("Cancelled")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/internal/v1/payments/refunds/batch")
                        .header(SECRET_HEADER, "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
