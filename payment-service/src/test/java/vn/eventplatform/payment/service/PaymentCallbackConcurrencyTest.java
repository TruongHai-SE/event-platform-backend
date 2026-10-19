package vn.eventplatform.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.payment.client.OrderServiceClient;
import vn.eventplatform.payment.client.dto.InternalOrderResponse;
import vn.eventplatform.payment.domain.Payment;
import vn.eventplatform.payment.domain.PaymentGateway;
import vn.eventplatform.payment.domain.PaymentStatus;
import vn.eventplatform.payment.dto.sepay.SepayWebhookRequest;
import vn.eventplatform.payment.dto.sepay.SepayWebhookResponse;
import vn.eventplatform.payment.mapper.PaymentMapper;
import vn.eventplatform.payment.mapper.RefundLogMapper;
import vn.eventplatform.payment.payment.PaymentStrategyFactory;
import vn.eventplatform.payment.payment.dto.PaymentCallbackResult;
import vn.eventplatform.payment.payment.sepay.config.SePayProperties;
import vn.eventplatform.payment.payment.strategy.PaymentStrategy;
import vn.eventplatform.payment.payment.vnpay.config.VnPayProperties;
import vn.eventplatform.payment.repository.PaymentRepository;
import vn.eventplatform.payment.repository.RefundLogRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCallbackConcurrencyTest {

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
    private SePayProperties sePayProperties;

    @Mock
    private PaymentStrategy paymentStrategy;

    private PaymentServiceImpl paymentService;

    private UUID orderId;
    private String orderCode;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        orderCode = "TKZ-20261019-CONCURRENT";

        paymentService = new PaymentServiceImpl(
                paymentRepository,
                refundLogRepository,
                paymentStrategyFactory,
                paymentMapper,
                refundLogMapper,
                orderServiceClient,
                vnPayProperties,
                sePayProperties
        );
        ReflectionTestUtils.setField(paymentService, "internalSecret", "secret123");
    }

    @Test
    @DisplayName("Concurrency: 10 luồng VNPay callback đồng thời chỉ có đúng 1 luồng chiến thắng transition PENDING->SUCCESS, 9 luồng còn lại nhận kết quả idempotent và markOrderPaid chỉ được gọi đúng 1 lần")
    void testConcurrentVnPayCallbacks_OnlyOneWinnerExecutesSideEffects() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        UUID paymentId = UUID.randomUUID();
        AtomicReference<PaymentStatus> dbStatus = new AtomicReference<>(PaymentStatus.PENDING);
        AtomicInteger markOrderPaidCallCount = new AtomicInteger(0);

        // Giả lập trạng thái DB thực tế: findByOrderCodeAndStatus trả về payment dựa theo dbStatus
        when(paymentRepository.findByOrderCodeAndStatus(eq(orderCode), eq(PaymentStatus.PENDING)))
                .thenAnswer(inv -> {
                    if (dbStatus.get() == PaymentStatus.PENDING) {
                        return List.of(Payment.builder()
                                .id(paymentId)
                                .orderId(orderId)
                                .orderCode(orderCode)
                                .method(PaymentGateway.VNPAY)
                                .amount(new BigDecimal("200000.00"))
                                .status(PaymentStatus.PENDING)
                                .build());
                    }
                    return List.of();
                });

        lenient().when(paymentRepository.findByOrderCodeOrderByCreatedAtDesc(eq(orderCode)))
                .thenAnswer(inv -> List.of(Payment.builder()
                        .id(paymentId)
                        .orderId(orderId)
                        .orderCode(orderCode)
                        .method(PaymentGateway.VNPAY)
                        .amount(new BigDecimal("200000.00"))
                        .status(dbStatus.get())
                        .build()));

        // Giả lập atomic conditional update trong DB: chỉ chuyển đổi PENDING -> SUCCESS đúng 1 lần duy nhất
        when(paymentRepository.updatePaymentStatusIfMatch(
                eq(paymentId), eq(PaymentStatus.SUCCESS), eq(PaymentStatus.PENDING), any(), any(), any()))
                .thenAnswer(inv -> {
                    boolean won = dbStatus.compareAndSet(PaymentStatus.PENDING, PaymentStatus.SUCCESS);
                    return won ? 1 : 0;
                });

        lenient().when(paymentRepository.findById(eq(paymentId))).thenAnswer(inv -> Optional.of(Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .orderCode(orderCode)
                .method(PaymentGateway.VNPAY)
                .amount(new BigDecimal("200000.00"))
                .status(dbStatus.get())
                .build()));

        when(paymentStrategyFactory.getStrategy(PaymentGateway.VNPAY)).thenReturn(paymentStrategy);
        when(paymentStrategy.verifyCallback(any())).thenAnswer(inv -> PaymentCallbackResult.builder()
                .success(true)
                .orderCode(orderCode)
                .transactionId("VNP_TXN_CONCURRENT")
                .amount(new BigDecimal("200000.00"))
                .payDate(Instant.now())
                .build());

        when(orderServiceClient.markOrderPaid(any(), any(), any())).thenAnswer(inv -> {
            markOrderPaidCallCount.incrementAndGet();
            return ApiResponse.ok(InternalOrderResponse.builder().id(orderId).status("PAID").build());
        });

        List<PaymentCallbackResult> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    PaymentCallbackResult res = paymentService.handleVnPayCallback(Map.of("vnp_TxnRef", orderCode));
                    results.add(res);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Bắn 10 luồng cùng lúc
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(results).hasSize(threadCount);
        assertThat(results).allMatch(PaymentCallbackResult::isSuccess);
        // Đảm bảo markOrderPaid chỉ được gọi đúng 1 lần duy nhất bởi luồng winner
        assertThat(markOrderPaidCallCount.get()).isEqualTo(1);
        assertThat(dbStatus.get()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("Concurrency: 10 luồng SePay webhook đồng thời chỉ có đúng 1 luồng chiến thắng transition PENDING->SUCCESS, 9 luồng còn lại nhận kết quả idempotent và markOrderPaid chỉ được gọi đúng 1 lần")
    void testConcurrentSepayWebhooks_OnlyOneWinnerExecutesSideEffects() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        UUID paymentId = UUID.randomUUID();
        String normalizedCode = "TKZ20261019CONCURRENT";
        AtomicReference<PaymentStatus> dbStatus = new AtomicReference<>(PaymentStatus.PENDING);
        AtomicInteger markOrderPaidCallCount = new AtomicInteger(0);

        when(sePayProperties.validateApiKey(any())).thenReturn(true);

        when(paymentRepository.findByNormalizedOrderCodeAndStatus(eq(normalizedCode), eq(PaymentStatus.PENDING)))
                .thenAnswer(inv -> {
                    if (dbStatus.get() == PaymentStatus.PENDING) {
                        return List.of(Payment.builder()
                                .id(paymentId)
                                .orderId(orderId)
                                .orderCode(orderCode)
                                .method(PaymentGateway.SEPAY)
                                .amount(new BigDecimal("150000.00"))
                                .status(PaymentStatus.PENDING)
                                .build());
                    }
                    return List.of();
                });

        lenient().when(paymentRepository.findByNormalizedOrderCode(eq(normalizedCode)))
                .thenAnswer(inv -> List.of(Payment.builder()
                        .id(paymentId)
                        .orderId(orderId)
                        .orderCode(orderCode)
                        .method(PaymentGateway.SEPAY)
                        .amount(new BigDecimal("150000.00"))
                        .status(dbStatus.get())
                        .build()));

        when(paymentRepository.updatePaymentStatusIfMatch(
                eq(paymentId), eq(PaymentStatus.SUCCESS), eq(PaymentStatus.PENDING), any(), any(), any()))
                .thenAnswer(inv -> {
                    boolean won = dbStatus.compareAndSet(PaymentStatus.PENDING, PaymentStatus.SUCCESS);
                    return won ? 1 : 0;
                });

        lenient().when(paymentRepository.findById(eq(paymentId))).thenAnswer(inv -> Optional.of(Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .orderCode(orderCode)
                .method(PaymentGateway.SEPAY)
                .amount(new BigDecimal("150000.00"))
                .status(dbStatus.get())
                .build()));

        when(orderServiceClient.markOrderPaid(any(), any(), any())).thenAnswer(inv -> {
            markOrderPaidCallCount.incrementAndGet();
            return ApiResponse.ok(InternalOrderResponse.builder().id(orderId).status("PAID").build());
        });

        List<SepayWebhookResponse> responses = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            long webhookId = 1000L + i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    SepayWebhookRequest req = SepayWebhookRequest.builder()
                            .id(webhookId)
                            .code(normalizedCode)
                            .transferAmount(new BigDecimal("150000.00"))
                            .referenceCode("REF-" + webhookId)
                            .build();
                    SepayWebhookResponse res = paymentService.handleSepayWebhook("valid_key", req);
                    responses.add(res);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(responses).hasSize(threadCount);
        assertThat(responses).allMatch(SepayWebhookResponse::isSuccess);
        // Đảm bảo markOrderPaid chỉ được gọi đúng 1 lần duy nhất
        assertThat(markOrderPaidCallCount.get()).isEqualTo(1);
        assertThat(dbStatus.get()).isEqualTo(PaymentStatus.SUCCESS);
    }
}
