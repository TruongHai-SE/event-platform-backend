package vn.eventplatform.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.payment.client.OrderServiceClient;
import vn.eventplatform.payment.client.dto.InternalOrderResponse;
import vn.eventplatform.payment.domain.Payment;
import vn.eventplatform.payment.domain.PaymentGateway;
import vn.eventplatform.payment.domain.PaymentStatus;
import vn.eventplatform.payment.dto.CreatePaymentRequest;
import vn.eventplatform.payment.dto.PaymentInitResponse;
import vn.eventplatform.payment.mapper.PaymentMapper;
import vn.eventplatform.payment.mapper.RefundLogMapper;
import vn.eventplatform.payment.payment.PaymentContext;
import vn.eventplatform.payment.payment.PaymentStrategyFactory;
import vn.eventplatform.payment.payment.dto.PaymentInitResult;
import vn.eventplatform.payment.payment.dto.PaymentCallbackResult;
import vn.eventplatform.payment.payment.strategy.PaymentStrategy;
import vn.eventplatform.payment.payment.vnpay.config.VnPayProperties;
import vn.eventplatform.payment.repository.PaymentRepository;
import vn.eventplatform.payment.repository.RefundLogRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundLogRepository refundLogRepository;

    @Mock
    private PaymentStrategyFactory paymentStrategyFactory;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private RefundLogMapper refundLogMapper;

    @Mock
    private OrderServiceClient orderServiceClient;

    @Mock
    private VnPayProperties vnPayProperties;

    @Mock
    private vn.eventplatform.payment.payment.sepay.config.SePayProperties sePayProperties;

    @Mock
    private PaymentStrategy paymentStrategy;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private UUID orderId;
    private InternalOrderResponse internalOrder;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        internalOrder = InternalOrderResponse.builder()
                .id(orderId)
                .orderCode("TKZ-20261018-001")
                .totalAmount(new BigDecimal("100000.00"))
                .status("PENDING")
                .customerEmail("test@example.com")
                .customerName("Nguyen Van A")
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        ReflectionTestUtils.setField(paymentService, "internalSecret", "secret123");
    }

    @Test
    @DisplayName("createPayment succeeds using backend configured returnUrl")
    void createPayment_Success_UsesBackendConfiguredReturnUrl() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .method(PaymentGateway.VNPAY)
                .build();

        when(orderServiceClient.getOrderById(eq("secret123"), eq(orderId)))
                .thenReturn(ApiResponse.ok(internalOrder));
        when(vnPayProperties.getReturnUrl()).thenReturn("http://localhost:5173/payment/vnpay-callback");

        Payment savedPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .orderCode("TKZ-20261018-001")
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(paymentStrategyFactory.getStrategy(PaymentGateway.VNPAY)).thenReturn(paymentStrategy);

        PaymentInitResult initResult = PaymentInitResult.builder()
                .paymentId(savedPayment.getId())
                .orderId(orderId)
                .method(PaymentGateway.VNPAY)
                .paymentUrl("https://sandbox.vnpayment.vn/pay")
                .build();

        when(paymentStrategy.createPaymentUrl(any(PaymentContext.class))).thenAnswer(invocation -> {
            PaymentContext ctx = invocation.getArgument(0);
            assertThat(ctx.getReturnUrl()).isEqualTo("http://localhost:5173/payment/vnpay-callback");
            return initResult;
        });

        PaymentInitResponse expectedResponse = PaymentInitResponse.builder()
                .paymentId(savedPayment.getId())
                .orderId(orderId)
                .paymentUrl("https://sandbox.vnpayment.vn/pay")
                .build();

        when(paymentMapper.toInitResponse(savedPayment, initResult)).thenReturn(expectedResponse);

        PaymentInitResponse response = paymentService.createPayment(request, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getPaymentUrl()).isEqualTo("https://sandbox.vnpayment.vn/pay");
        verify(vnPayProperties).getReturnUrl();
    }

    @Test
    @DisplayName("handleSepayWebhook throws UNAUTHORIZED when API key is invalid")
    void handleSepayWebhook_InvalidApiKey() {
        when(sePayProperties.validateApiKey("invalid_key")).thenReturn(false);

        vn.eventplatform.payment.dto.sepay.SepayWebhookRequest request = vn.eventplatform.payment.dto.sepay.SepayWebhookRequest.builder()
                .code("TKZ123456")
                .transferAmount(BigDecimal.valueOf(100000))
                .build();

        assertThatThrownBy(() -> paymentService.handleSepayWebhook("invalid_key", request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("API Key SePay không hợp lệ");
    }

    @Test
    @DisplayName("handleSepayWebhook returns success true even when order not found (simulation test)")
    void handleSepayWebhook_OrderNotFound_ReturnsSuccessTrue() {
        when(sePayProperties.validateApiKey(any())).thenReturn(true);
        when(paymentRepository.findByNormalizedOrderCodeAndStatus("TKZAL97V2V66WDQRCA93T", PaymentStatus.PENDING))
                .thenReturn(List.of());
        when(paymentRepository.findByNormalizedOrderCode("TKZAL97V2V66WDQRCA93T"))
                .thenReturn(List.of());

        vn.eventplatform.payment.dto.sepay.SepayWebhookRequest request = vn.eventplatform.payment.dto.sepay.SepayWebhookRequest.builder()
                .code("TKZAL97V2V66WDQRCA93T")
                .transferAmount(BigDecimal.valueOf(100000))
                .content("Thanh toan don hang TKZAL97V2V66WDQRCA93T")
                .build();

        vn.eventplatform.payment.dto.sepay.SepayWebhookResponse response = paymentService.handleSepayWebhook("Apikey sepay_secret_tkz_2026", request);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("handleVnPayCallback marks payment as FAILED and returns success false when amount mismatches")
    void handleVnPayCallback_AmountMismatch() {
        String orderCode = "TKZ-20261018-001";
        Payment existingPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .orderCode(orderCode)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.PENDING)
                .build();

        PaymentCallbackResult callbackResult = PaymentCallbackResult.builder()
                .success(true)
                .orderCode(orderCode)
                .transactionId("VNP123456")
                .amount(new BigDecimal("50000.00")) // mismatched amount
                .build();

        when(paymentStrategyFactory.getStrategy(PaymentGateway.VNPAY)).thenReturn(paymentStrategy);
        when(paymentStrategy.verifyCallback(any())).thenReturn(callbackResult);
        when(paymentRepository.findByOrderCodeAndStatus(orderCode, PaymentStatus.PENDING))
                .thenReturn(List.of(existingPayment));

        PaymentCallbackResult result = paymentService.handleVnPayCallback(Map.of("vnp_TxnRef", orderCode));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Số tiền thanh toán VNPay không khớp");
        assertThat(existingPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(existingPayment.getTransactionId()).isEqualTo("VNP123456");
        verify(paymentRepository).save(existingPayment);
    }
}
