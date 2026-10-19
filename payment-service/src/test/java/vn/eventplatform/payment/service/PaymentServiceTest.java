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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import vn.eventplatform.common.messaging.EventPlatformMessagingConfig;
import vn.eventplatform.common.messaging.EventPlatformMessagingConfig.CommonEventPublisher;
import vn.eventplatform.common.messaging.event.PaymentFailedEventPayload;
import vn.eventplatform.payment.dto.sepay.SepayWebhookRequest;
import vn.eventplatform.payment.dto.sepay.SepayWebhookResponse;

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

    @Mock
    private CommonEventPublisher eventPublisher;

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
        paymentService.setEventPublisher(eventPublisher);

        lenient().when(paymentRepository.updatePaymentStatusIfMatch(any(), any(), any(), any(), any(), any())).thenReturn(1);
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
        verify(paymentRepository).updatePaymentStatusIfMatch(eq(existingPayment.getId()), eq(PaymentStatus.FAILED), eq(PaymentStatus.PENDING), eq("VNP123456"), any(), any());
    }

    @Test
    @DisplayName("retryPayment cancels previous PENDING payments and initializes new gateway payment")
    void retryPayment_CancelsPreviousPendingPayments_AndInitializesNewGateway() {
        Payment oldVnpayPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.VNPAY)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.existsByOrderIdAndStatusIn(eq(orderId), eq(List.of(PaymentStatus.SUCCESS))))
                .thenReturn(false);
        when(paymentRepository.findByOrderIdAndStatus(eq(orderId), eq(PaymentStatus.PENDING)))
                .thenReturn(List.of(oldVnpayPayment));

        Payment newSepayPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.SEPAY)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(newSepayPayment);
        when(paymentStrategyFactory.getStrategy(PaymentGateway.SEPAY)).thenReturn(paymentStrategy);

        PaymentInitResult initResult = PaymentInitResult.builder()
                .paymentId(newSepayPayment.getId())
                .orderId(orderId)
                .method(PaymentGateway.SEPAY)
                .paymentUrl("https://qr.sepay.vn/img?des=TKZ20261018001")
                .qrCode("https://qr.sepay.vn/img?des=TKZ20261018001")
                .build();

        when(paymentStrategy.createPaymentUrl(any(PaymentContext.class))).thenReturn(initResult);

        PaymentInitResponse mappedResponse = PaymentInitResponse.builder()
                .paymentId(newSepayPayment.getId())
                .orderId(orderId)
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.SEPAY)
                .amount(new BigDecimal("100000.00"))
                .paymentUrl("https://qr.sepay.vn/img?des=TKZ20261018001")
                .qrCode("https://qr.sepay.vn/img?des=TKZ20261018001")
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentMapper.toInitResponse(newSepayPayment, initResult)).thenReturn(mappedResponse);

        Instant originalExpiresAt = internalOrder.getExpiresAt();
        vn.eventplatform.payment.dto.InternalRetryPaymentRequest retryRequest =
                vn.eventplatform.payment.dto.InternalRetryPaymentRequest.builder()
                        .orderId(orderId)
                        .orderCode("TKZ-20261018-001")
                        .amount(new BigDecimal("100000.00"))
                        .method(PaymentGateway.SEPAY)
                        .expiresAt(originalExpiresAt)
                        .customerName("Nguyen Van A")
                        .customerEmail("test@example.com")
                        .clientIp("127.0.0.1")
                        .build();

        PaymentInitResponse response = paymentService.retryPayment(retryRequest);

        assertThat(oldVnpayPayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentRepository).saveAll(List.of(oldVnpayPayment));
        assertThat(response).isNotNull();
        assertThat(response.getMethod()).isEqualTo(PaymentGateway.SEPAY);
        assertThat(response.getExpiresAt()).isEqualTo(originalExpiresAt);
    }

    @Test
    @DisplayName("retryPayment throws BAD_REQUEST when order already has SUCCESS payment")
    void retryPayment_ThrowsBadRequest_WhenOrderAlreadyPaid() {
        when(paymentRepository.existsByOrderIdAndStatusIn(eq(orderId), eq(List.of(PaymentStatus.SUCCESS))))
                .thenReturn(true);

        vn.eventplatform.payment.dto.InternalRetryPaymentRequest retryRequest =
                vn.eventplatform.payment.dto.InternalRetryPaymentRequest.builder()
                        .orderId(orderId)
                        .orderCode("TKZ-20261018-001")
                        .amount(new BigDecimal("100000.00"))
                        .method(PaymentGateway.SEPAY)
                        .expiresAt(Instant.now().plusSeconds(600))
                        .build();

        assertThatThrownBy(() -> paymentService.retryPayment(retryRequest))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Đơn hàng này đã có giao dịch thanh toán thành công");
    }

    @Test
    @DisplayName("retryPayment throws BAD_REQUEST when order expiresAt is in the past")
    void retryPayment_ThrowsBadRequest_WhenOrderExpired() {
        when(paymentRepository.existsByOrderIdAndStatusIn(eq(orderId), eq(List.of(PaymentStatus.SUCCESS))))
                .thenReturn(false);

        vn.eventplatform.payment.dto.InternalRetryPaymentRequest retryRequest =
                vn.eventplatform.payment.dto.InternalRetryPaymentRequest.builder()
                        .orderId(orderId)
                        .orderCode("TKZ-20261018-001")
                        .amount(new BigDecimal("100000.00"))
                        .method(PaymentGateway.SEPAY)
                        .expiresAt(Instant.now().minusSeconds(60))
                        .build();

        assertThatThrownBy(() -> paymentService.retryPayment(retryRequest))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Đơn hàng đã hết thời gian giữ chỗ thanh toán");
    }

    @Test
    @DisplayName("Publish event payment.failed khi callback VNPay trả về trạng thái thất bại")
    void handleVnPayCallback_PublishesPaymentFailedEvent_WhenStatusFailed() {
        Payment pendingPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.VNPAY)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentStrategyFactory.getStrategy(PaymentGateway.VNPAY)).thenReturn(paymentStrategy);
        PaymentCallbackResult failedResult = PaymentCallbackResult.builder()
                .success(false)
                .orderCode("TKZ-20261018-001")
                .transactionId("VNP_TXN_FAIL_01")
                .message("Khách hàng hủy giao dịch")
                .build();
        when(paymentStrategy.verifyCallback(any())).thenReturn(failedResult);

        when(paymentRepository.findByOrderCodeAndStatus("TKZ-20261018-001", PaymentStatus.PENDING))
                .thenReturn(List.of(pendingPayment));

        paymentService.handleVnPayCallback(Map.of("vnp_TxnRef", "TKZ-20261018-001"));

        assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(pendingPayment.getTransactionId()).isEqualTo("VNP_TXN_FAIL_01");

        ArgumentCaptor<PaymentFailedEventPayload> payloadCaptor = ArgumentCaptor.forClass(PaymentFailedEventPayload.class);
        verify(eventPublisher).publish(
                eq(EventPlatformMessagingConfig.RK_PAYMENT_ORDER_FAILED),
                eq(EventPlatformMessagingConfig.EVENT_TYPE_PAYMENT_FAILED),
                payloadCaptor.capture()
        );

        PaymentFailedEventPayload published = payloadCaptor.getValue();
        assertThat(published.getOrderId()).isEqualTo(orderId);
        assertThat(published.getOrderCode()).isEqualTo("TKZ-20261018-001");
        assertThat(published.getAmount()).isEqualByComparingTo("100000.00");
        assertThat(published.getFailureReason()).isEqualTo("Khách hàng hủy giao dịch");
    }

    @Test
    @DisplayName("Publish event payment.failed khi số tiền callback VNPay không khớp số tiền đơn hàng")
    void handleVnPayCallback_PublishesPaymentFailedEvent_WhenAmountMismatch() {
        Payment pendingPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.VNPAY)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentStrategyFactory.getStrategy(PaymentGateway.VNPAY)).thenReturn(paymentStrategy);
        PaymentCallbackResult mismatchResult = PaymentCallbackResult.builder()
                .success(true)
                .orderCode("TKZ-20261018-001")
                .amount(new BigDecimal("50000.00"))
                .transactionId("VNP_TXN_MISMATCH_01")
                .build();
        when(paymentStrategy.verifyCallback(any())).thenReturn(mismatchResult);

        when(paymentRepository.findByOrderCodeAndStatus("TKZ-20261018-001", PaymentStatus.PENDING))
                .thenReturn(List.of(pendingPayment));

        PaymentCallbackResult result = paymentService.handleVnPayCallback(Map.of("vnp_TxnRef", "TKZ-20261018-001"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        ArgumentCaptor<PaymentFailedEventPayload> payloadCaptor = ArgumentCaptor.forClass(PaymentFailedEventPayload.class);
        verify(eventPublisher).publish(
                eq(EventPlatformMessagingConfig.RK_PAYMENT_ORDER_FAILED),
                eq(EventPlatformMessagingConfig.EVENT_TYPE_PAYMENT_FAILED),
                payloadCaptor.capture()
        );

        PaymentFailedEventPayload published = payloadCaptor.getValue();
        assertThat(published.getOrderId()).isEqualTo(orderId);
        assertThat(published.getFailureReason()).contains("Số tiền thanh toán VNPay không khớp");
    }

    @Test
    @DisplayName("Publish event payment.failed khi số tiền SePay chuyển khoản không đủ")
    void handleSepayWebhook_PublishesPaymentFailedEvent_WhenTransferAmountInsufficient() {
        when(sePayProperties.validateApiKey("valid_key")).thenReturn(true);

        Payment pendingPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.SEPAY)
                .amount(new BigDecimal("150000.00"))
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByNormalizedOrderCodeAndStatus("TKZ20261018001", PaymentStatus.PENDING))
                .thenReturn(List.of(pendingPayment));

        SepayWebhookRequest webhookRequest = SepayWebhookRequest.builder()
                .id(12345L)
                .code("TKZ20261018001")
                .transferAmount(new BigDecimal("100000.00"))
                .build();

        SepayWebhookResponse response = paymentService.handleSepayWebhook("valid_key", webhookRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("Số tiền chuyển khoản không đủ");

        ArgumentCaptor<PaymentFailedEventPayload> payloadCaptor = ArgumentCaptor.forClass(PaymentFailedEventPayload.class);
        verify(eventPublisher).publish(
                eq(EventPlatformMessagingConfig.RK_PAYMENT_ORDER_FAILED),
                eq(EventPlatformMessagingConfig.EVENT_TYPE_PAYMENT_FAILED),
                payloadCaptor.capture()
        );

        PaymentFailedEventPayload published = payloadCaptor.getValue();
        assertThat(published.getOrderId()).isEqualTo(orderId);
        assertThat(published.getFailureReason()).isEqualTo("Số tiền chuyển khoản SePay không đủ");
    }

    @Test
    @DisplayName("createPayment succeeds using orderCode and SePay gateway")
    void createPayment_Success_WithOrderCode() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.SEPAY)
                .build();

        when(orderServiceClient.getOrderByCode(eq("secret123"), eq("TKZ-20261018-001")))
                .thenReturn(ApiResponse.ok(internalOrder));

        Payment savedPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .orderCode("TKZ-20261018-001")
                .amount(new BigDecimal("100000.00"))
                .method(PaymentGateway.SEPAY)
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(paymentStrategyFactory.getStrategy(PaymentGateway.SEPAY)).thenReturn(paymentStrategy);

        PaymentInitResult initResult = PaymentInitResult.builder()
                .paymentId(savedPayment.getId())
                .orderId(orderId)
                .method(PaymentGateway.SEPAY)
                .paymentUrl("https://qr.sepay.vn/img?des=TKZ20261018001")
                .qrCode("https://qr.sepay.vn/img?des=TKZ20261018001")
                .build();

        when(paymentStrategy.createPaymentUrl(any(PaymentContext.class))).thenReturn(initResult);

        PaymentInitResponse expectedResponse = PaymentInitResponse.builder()
                .paymentId(savedPayment.getId())
                .orderId(orderId)
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.SEPAY)
                .paymentUrl("https://qr.sepay.vn/img?des=TKZ20261018001")
                .qrCode("https://qr.sepay.vn/img?des=TKZ20261018001")
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentMapper.toInitResponse(savedPayment, initResult)).thenReturn(expectedResponse);

        PaymentInitResponse response = paymentService.createPayment(request, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getMethod()).isEqualTo(PaymentGateway.SEPAY);
        assertThat(response.getQrCode()).contains("sepay");
        verify(orderServiceClient).getOrderByCode("secret123", "TKZ-20261018-001");
    }

    @Test
    @DisplayName("createPayment throws NOT_FOUND when order does not exist")
    void createPayment_ThrowsNotFound_WhenOrderDoesNotExist() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderCode("TKZ-NON-EXISTENT")
                .method(PaymentGateway.VNPAY)
                .build();

        when(orderServiceClient.getOrderByCode(eq("secret123"), eq("TKZ-NON-EXISTENT")))
                .thenReturn(ApiResponse.error("404", "Order not found"));

        assertThatThrownBy(() -> paymentService.createPayment(request, "127.0.0.1"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Không tìm thấy thông tin đơn hàng");
    }

    @Test
    @DisplayName("createPayment throws BAD_REQUEST when order status is not PENDING")
    void createPayment_ThrowsBadRequest_WhenOrderStatusNotPending() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.VNPAY)
                .build();

        InternalOrderResponse paidOrder = InternalOrderResponse.builder()
                .id(orderId)
                .orderCode("TKZ-20261018-001")
                .status("PAID")
                .build();

        when(orderServiceClient.getOrderByCode(eq("secret123"), eq("TKZ-20261018-001")))
                .thenReturn(ApiResponse.ok(paidOrder));

        assertThatThrownBy(() -> paymentService.createPayment(request, "127.0.0.1"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Đơn hàng không ở trạng thái chờ thanh toán");
    }

    @Test
    @DisplayName("createPayment throws BAD_REQUEST when order expired")
    void createPayment_ThrowsBadRequest_WhenOrderExpired() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.VNPAY)
                .build();

        InternalOrderResponse expiredOrder = InternalOrderResponse.builder()
                .id(orderId)
                .orderCode("TKZ-20261018-001")
                .status("PENDING")
                .expiresAt(Instant.now().minusSeconds(100))
                .build();

        when(orderServiceClient.getOrderByCode(eq("secret123"), eq("TKZ-20261018-001")))
                .thenReturn(ApiResponse.ok(expiredOrder));

        assertThatThrownBy(() -> paymentService.createPayment(request, "127.0.0.1"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Đơn hàng đã hết thời gian giữ chỗ thanh toán");
    }

    @Test
    @DisplayName("createPayment throws FORBIDDEN when user does not own order (IDOR defense)")
    void createPayment_ThrowsForbidden_WhenIdorMismatch() {
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.VNPAY)
                .build();

        InternalOrderResponse orderWithOwner = InternalOrderResponse.builder()
                .id(orderId)
                .orderCode("TKZ-20261018-001")
                .userId(ownerId)
                .status("PENDING")
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        when(orderServiceClient.getOrderByCode(eq("secret123"), eq("TKZ-20261018-001")))
                .thenReturn(ApiResponse.ok(orderWithOwner));

        assertThatThrownBy(() -> paymentService.createPayment(request, attackerId, false, "127.0.0.1"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Bạn không có quyền thao tác trên đơn hàng này");
    }

    @Test
    @DisplayName("createPayment succeeds for ADMIN even if userId does not match owner")
    void createPayment_SucceedsForAdmin_EvenIfUserIdDiffers() {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.VNPAY)
                .build();

        InternalOrderResponse orderWithOwner = InternalOrderResponse.builder()
                .id(orderId)
                .orderCode("TKZ-20261018-001")
                .userId(ownerId)
                .totalAmount(new BigDecimal("100000.00"))
                .status("PENDING")
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        when(orderServiceClient.getOrderByCode(eq("secret123"), eq("TKZ-20261018-001")))
                .thenReturn(ApiResponse.ok(orderWithOwner));

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

        when(paymentStrategy.createPaymentUrl(any(PaymentContext.class))).thenReturn(initResult);
        when(paymentMapper.toInitResponse(any(), any())).thenReturn(PaymentInitResponse.builder()
                .paymentId(savedPayment.getId())
                .orderId(orderId)
                .orderCode("TKZ-20261018-001")
                .build());

        PaymentInitResponse response = paymentService.createPayment(request, adminId, true, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("getPaymentsByOrderCode returns mapped payments")
    void getPaymentsByOrderCode_ReturnsList() {
        String code = "TKZ-20261018-001";
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .orderCode(code)
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByOrderCodeOrderByCreatedAtDesc(code))
                .thenReturn(List.of(payment));
        when(paymentMapper.toResponseList(List.of(payment)))
                .thenReturn(List.of(vn.eventplatform.payment.dto.PaymentResponse.builder()
                        .id(payment.getId())
                        .orderCode(code)
                        .status(PaymentStatus.PENDING)
                        .build()));

        List<vn.eventplatform.payment.dto.PaymentResponse> responses =
                paymentService.getPaymentsByOrderCode(code, null, false);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getOrderCode()).isEqualTo(code);
    }

    @Test
    @DisplayName("handleVnPayCallback: Luồng winner thực hiện transition PENDING->SUCCESS và gọi markOrderPaid")
    void handleVnPayCallback_Winner_Success_TransitionsToSuccessAndCallsOrderService() {
        String orderCode = "TKZ-20261018-001";
        Payment pendingPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .orderCode(orderCode)
                .method(PaymentGateway.VNPAY)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.PENDING)
                .build();

        PaymentCallbackResult callbackResult = PaymentCallbackResult.builder()
                .success(true)
                .orderCode(orderCode)
                .transactionId("VNP12345678")
                .amount(new BigDecimal("100000.00"))
                .payDate(Instant.now())
                .build();

        when(paymentStrategyFactory.getStrategy(PaymentGateway.VNPAY)).thenReturn(paymentStrategy);
        when(paymentStrategy.verifyCallback(any())).thenReturn(callbackResult);
        when(paymentRepository.findByOrderCodeAndStatus(orderCode, PaymentStatus.PENDING))
                .thenReturn(List.of(pendingPayment));
        when(paymentRepository.updatePaymentStatusIfMatch(
                eq(pendingPayment.getId()), eq(PaymentStatus.SUCCESS), eq(PaymentStatus.PENDING), eq("VNP12345678"), any(), any()))
                .thenReturn(1);
        when(orderServiceClient.markOrderPaid(eq("secret123"), eq(orderId), any()))
                .thenReturn(ApiResponse.ok(internalOrder));

        PaymentCallbackResult result = paymentService.handleVnPayCallback(Map.of("vnp_TxnRef", orderCode));

        assertThat(result.isSuccess()).isTrue();
        assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(pendingPayment.getTransactionId()).isEqualTo("VNP12345678");
        verify(paymentRepository).updatePaymentStatusIfMatch(
                eq(pendingPayment.getId()), eq(PaymentStatus.SUCCESS), eq(PaymentStatus.PENDING), eq("VNP12345678"), any(), any());
        verify(paymentRepository).cancelOtherPendingPayments(eq(orderId), eq(pendingPayment.getId()), eq(PaymentStatus.CANCELLED), eq(PaymentStatus.PENDING), any());
        verify(orderServiceClient).markOrderPaid(eq("secret123"), eq(orderId), any());
    }

    @Test
    @DisplayName("handleVnPayCallback: Trả về idempotent ngay khi Payment đã ở trạng thái SUCCESS, không gọi markOrderPaid")
    void handleVnPayCallback_Idempotent_WhenAlreadySuccess() {
        String orderCode = "TKZ-20261018-001";
        Payment successPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .orderCode(orderCode)
                .method(PaymentGateway.VNPAY)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.SUCCESS)
                .transactionId("VNP12345678")
                .build();

        PaymentCallbackResult callbackResult = PaymentCallbackResult.builder()
                .success(true)
                .orderCode(orderCode)
                .transactionId("VNP12345678")
                .amount(new BigDecimal("100000.00"))
                .build();

        when(paymentStrategyFactory.getStrategy(PaymentGateway.VNPAY)).thenReturn(paymentStrategy);
        when(paymentStrategy.verifyCallback(any())).thenReturn(callbackResult);
        when(paymentRepository.findByOrderCodeAndStatus(orderCode, PaymentStatus.PENDING))
                .thenReturn(List.of());
        when(paymentRepository.findByOrderCodeOrderByCreatedAtDesc(orderCode))
                .thenReturn(List.of(successPayment));

        PaymentCallbackResult result = paymentService.handleVnPayCallback(Map.of("vnp_TxnRef", orderCode));

        assertThat(result.isSuccess()).isTrue();
        verify(paymentRepository, never()).updatePaymentStatusIfMatch(any(), any(), any(), any(), any(), any());
        verify(orderServiceClient, never()).markOrderPaid(any(), any(), any());
    }

    @Test
    @DisplayName("handleVnPayCallback: Luồng runner-up phát hiện luồng khác đã update SUCCESS thì trả về idempotent không lặp lại side-effects")
    void handleVnPayCallback_RaceCondition_RunnerUpReturnsIdempotentResponse() {
        String orderCode = "TKZ-20261018-001";
        UUID paymentId = UUID.randomUUID();
        Payment initialPendingPayment = Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .orderCode(orderCode)
                .method(PaymentGateway.VNPAY)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.PENDING)
                .build();

        Payment reloadedSuccessPayment = Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .orderCode(orderCode)
                .method(PaymentGateway.VNPAY)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.SUCCESS)
                .transactionId("VNP12345678")
                .build();

        PaymentCallbackResult callbackResult = PaymentCallbackResult.builder()
                .success(true)
                .orderCode(orderCode)
                .transactionId("VNP12345678")
                .amount(new BigDecimal("100000.00"))
                .build();

        when(paymentStrategyFactory.getStrategy(PaymentGateway.VNPAY)).thenReturn(paymentStrategy);
        when(paymentStrategy.verifyCallback(any())).thenReturn(callbackResult);
        when(paymentRepository.findByOrderCodeAndStatus(orderCode, PaymentStatus.PENDING))
                .thenReturn(List.of(initialPendingPayment));
        // Winner luồng khác đã update nên update trả về 0
        when(paymentRepository.updatePaymentStatusIfMatch(
                eq(paymentId), eq(PaymentStatus.SUCCESS), eq(PaymentStatus.PENDING), eq("VNP12345678"), any(), any()))
                .thenReturn(0);
        when(paymentRepository.findById(paymentId)).thenReturn(java.util.Optional.of(reloadedSuccessPayment));

        PaymentCallbackResult result = paymentService.handleVnPayCallback(Map.of("vnp_TxnRef", orderCode));

        assertThat(result.isSuccess()).isTrue();
        verify(orderServiceClient, never()).markOrderPaid(any(), any(), any());
    }

    @Test
    @DisplayName("handleSepayWebhook: Luồng winner transition PENDING->SUCCESS và gọi markOrderPaid")
    void handleSepayWebhook_Winner_Success_TransitionsToSuccessAndCallsOrderService() {
        when(sePayProperties.validateApiKey("valid_key")).thenReturn(true);

        Payment pendingPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.SEPAY)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByNormalizedOrderCodeAndStatus("TKZ20261018001", PaymentStatus.PENDING))
                .thenReturn(List.of(pendingPayment));
        when(paymentRepository.updatePaymentStatusIfMatch(
                eq(pendingPayment.getId()), eq(PaymentStatus.SUCCESS), eq(PaymentStatus.PENDING), any(), any(), any()))
                .thenReturn(1);
        when(orderServiceClient.markOrderPaid(eq("secret123"), eq(orderId), any()))
                .thenReturn(ApiResponse.ok(internalOrder));

        SepayWebhookRequest webhookRequest = SepayWebhookRequest.builder()
                .id(99999L)
                .code("TKZ20261018001")
                .transferAmount(new BigDecimal("100000.00"))
                .referenceCode("REF99999")
                .build();

        SepayWebhookResponse response = paymentService.handleSepayWebhook("valid_key", webhookRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("thành công");
        assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(pendingPayment.getTransactionId()).isEqualTo("REF99999");
        verify(paymentRepository).updatePaymentStatusIfMatch(
                eq(pendingPayment.getId()), eq(PaymentStatus.SUCCESS), eq(PaymentStatus.PENDING), eq("REF99999"), any(), any());
        verify(paymentRepository).cancelOtherPendingPayments(eq(orderId), eq(pendingPayment.getId()), eq(PaymentStatus.CANCELLED), eq(PaymentStatus.PENDING), any());
        verify(orderServiceClient).markOrderPaid(eq("secret123"), eq(orderId), any());
    }

    @Test
    @DisplayName("handleSepayWebhook: Trả về idempotent ngay khi Payment đã ở trạng thái SUCCESS, không gọi markOrderPaid")
    void handleSepayWebhook_Idempotent_WhenAlreadySuccess() {
        when(sePayProperties.validateApiKey("valid_key")).thenReturn(true);

        Payment successPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.SEPAY)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.SUCCESS)
                .transactionId("REF99999")
                .build();

        when(paymentRepository.findByNormalizedOrderCodeAndStatus("TKZ20261018001", PaymentStatus.PENDING))
                .thenReturn(List.of());
        when(paymentRepository.findByNormalizedOrderCode("TKZ20261018001"))
                .thenReturn(List.of(successPayment));

        SepayWebhookRequest webhookRequest = SepayWebhookRequest.builder()
                .id(99999L)
                .code("TKZ20261018001")
                .transferAmount(new BigDecimal("100000.00"))
                .referenceCode("REF99999")
                .build();

        SepayWebhookResponse response = paymentService.handleSepayWebhook("valid_key", webhookRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("trước đó");
        verify(paymentRepository, never()).updatePaymentStatusIfMatch(any(), any(), any(), any(), any(), any());
        verify(orderServiceClient, never()).markOrderPaid(any(), any(), any());
    }

    @Test
    @DisplayName("handleSepayWebhook: Luồng runner-up phát hiện luồng khác đã update SUCCESS thì trả về idempotent")
    void handleSepayWebhook_RaceCondition_RunnerUpReturnsIdempotentResponse() {
        when(sePayProperties.validateApiKey("valid_key")).thenReturn(true);

        UUID paymentId = UUID.randomUUID();
        Payment initialPendingPayment = Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.SEPAY)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.PENDING)
                .build();

        Payment reloadedSuccessPayment = Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .orderCode("TKZ-20261018-001")
                .method(PaymentGateway.SEPAY)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatus.SUCCESS)
                .transactionId("REF99999")
                .build();

        when(paymentRepository.findByNormalizedOrderCodeAndStatus("TKZ20261018001", PaymentStatus.PENDING))
                .thenReturn(List.of(initialPendingPayment));
        when(paymentRepository.updatePaymentStatusIfMatch(
                eq(paymentId), eq(PaymentStatus.SUCCESS), eq(PaymentStatus.PENDING), any(), any(), any()))
                .thenReturn(0);
        when(paymentRepository.findById(paymentId)).thenReturn(java.util.Optional.of(reloadedSuccessPayment));

        SepayWebhookRequest webhookRequest = SepayWebhookRequest.builder()
                .id(99999L)
                .code("TKZ20261018001")
                .transferAmount(new BigDecimal("100000.00"))
                .referenceCode("REF99999")
                .build();

        SepayWebhookResponse response = paymentService.handleSepayWebhook("valid_key", webhookRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("trước đó");
        verify(orderServiceClient, never()).markOrderPaid(any(), any(), any());
    }
}

