package vn.eventplatform.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.eventplatform.common.config.CommonOpenApiConfig;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.common.exception.GlobalExceptionHandler;
import vn.eventplatform.common.messaging.EventEnvelope;
import vn.eventplatform.common.messaging.EventPlatformMessagingConfig;
import vn.eventplatform.common.messaging.EventPlatformMessagingConfig.CommonEventPublisher;
import vn.eventplatform.common.messaging.EventPlatformMessagingConfig.IdempotentConsumerTemplate;
import vn.eventplatform.common.messaging.EventPlatformMessagingConfig.NonRetryableEventException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CommonBasePlatformTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("1. GlobalExceptionHandler: Chặn lộ SQL/Stacktrace & Chuẩn hóa mã lỗi 400, 404, 409, 500")
    void testGlobalExceptionHandlerAntiSabotage() throws Exception {
        // 1.1 Malformed JSON -> 400 ERR_BAD_REQUEST
        HttpMessageNotReadableException malformedEx = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character",
                new MockHttpInputMessage("{bad".getBytes(StandardCharsets.UTF_8)));
        ResponseEntity<ApiResponse<Void>> r1 = exceptionHandler.handleMessageNotReadable(malformedEx);
        assertEquals(HttpStatus.BAD_REQUEST, r1.getStatusCode());
        assertNotNull(r1.getBody());
        assertFalse(r1.getBody().isSuccess());
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), r1.getBody().getCode());

        // 1.2 Type Mismatch (e.g. non-UUID in @PathVariable) -> 400 ERR_BAD_REQUEST
        MethodArgumentTypeMismatchException mismatchEx = new MethodArgumentTypeMismatchException(
                "not-a-uuid", UUID.class, "eventId", null, new IllegalArgumentException("Invalid UUID"));
        ResponseEntity<ApiResponse<Void>> r2 = exceptionHandler.handleTypeMismatch(mismatchEx);
        assertEquals(HttpStatus.BAD_REQUEST, r2.getStatusCode());
        assertNotNull(r2.getBody());
        assertTrue(r2.getBody().getMessage().contains("eventId"));

        // 1.3 Missing Request Parameter -> 400 ERR_BAD_REQUEST
        MissingServletRequestParameterException missingParamEx =
                new MissingServletRequestParameterException("page", "int");
        ResponseEntity<ApiResponse<Void>> r3 = exceptionHandler.handleMissingParameter(missingParamEx);
        assertEquals(HttpStatus.BAD_REQUEST, r3.getStatusCode());
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), r3.getBody().getCode());

        // 1.4 DataIntegrityViolationException -> 409 ERR_CONFLICT (TUYỆT ĐỐI KHÔNG LỘ CÂU LỆNH SQL!)
        DataIntegrityViolationException sqlEx = new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"uk_accounts_email\" Detail: Key (email)=(a@b.com) already exists.");
        ResponseEntity<ApiResponse<Void>> r4 = exceptionHandler.handleDataIntegrityViolation(sqlEx);
        assertEquals(HttpStatus.CONFLICT, r4.getStatusCode());
        assertEquals(ErrorCode.CONFLICT.getCode(), r4.getBody().getCode());
        assertFalse(r4.getBody().getMessage().contains("uk_accounts_email"));
        assertFalse(r4.getBody().getMessage().contains("duplicate key"));

        // 1.5 Optimistic Locking Conflict -> 409 ERR_CONFLICT
        ObjectOptimisticLockingFailureException lockEx =
                new ObjectOptimisticLockingFailureException("TicketInventory", UUID.randomUUID());
        ResponseEntity<ApiResponse<Void>> r5 = exceptionHandler.handleConcurrencyLockingFailure(lockEx);
        assertEquals(HttpStatus.CONFLICT, r5.getStatusCode());
        assertEquals(ErrorCode.CONFLICT.getCode(), r5.getBody().getCode());

        // 1.6 Unhandled RuntimeException -> 500 ERR_INTERNAL_SERVER (KHÔNG LỘ ex.getMessage() nội bộ!)
        RuntimeException rawEx = new RuntimeException("org.postgresql.util.PSQLException: FATAL: password authentication failed");
        ResponseEntity<ApiResponse<Void>> r6 = exceptionHandler.handleGeneralException(rawEx);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, r6.getStatusCode());
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), r6.getBody().getCode());
        assertFalse(r6.getBody().getMessage().contains("PSQLException"));

        // 1.7 AppException -> đúng HttpStatus & ErrorCode
        AppException appEx = new AppException(ErrorCode.TOO_MANY_REQUESTS, "Quá giới hạn OTP");
        ResponseEntity<ApiResponse<Void>> r7 = exceptionHandler.handleAppException(appEx);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, r7.getStatusCode());
        assertEquals("ERR_TOO_MANY_REQUESTS", r7.getBody().getCode());
    }

    @Test
    @DisplayName("2. CommonOpenApiConfig & CorrelationIdFilter: Chuẩn hóa BearerAuth JWT & Chống rò rỉ MDC")
    void testCommonOpenApiConfigAndCorrelationIdMdcCleanup() throws Exception {
        CommonOpenApiConfig config = new CommonOpenApiConfig();
        OpenAPI openAPI = config.eventPlatformOpenAPI("event-service");
        assertNotNull(openAPI.getInfo());
        assertTrue(openAPI.getInfo().getTitle().contains("event-service"));
        assertNotNull(openAPI.getComponents().getSecuritySchemes().get(CommonOpenApiConfig.SECURITY_SCHEME_NAME));
        assertEquals("JWT", openAPI.getComponents().getSecuritySchemes()
                .get(CommonOpenApiConfig.SECURITY_SCHEME_NAME).getBearerFormat());

        // Verify CorrelationIdFilter populates MDC during request and cleans up MDC in finally
        CommonOpenApiConfig.CorrelationIdFilter filter = new CommonOpenApiConfig.CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events/public");
        request.addHeader("X-Request-Id", "req-mdc-test-999");
        request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertEquals("req-mdc-test-999", MDC.get("requestId"));
            assertEquals("req-mdc-test-999", MDC.get("X-Request-Id"));
            assertNotNull(MDC.get("traceparent"));
        });

        assertEquals("req-mdc-test-999", response.getHeader("X-Request-Id"));
        // After filter completes, MDC MUST be completely cleaned up!
        assertNull(MDC.get("requestId"));
        assertNull(MDC.get("X-Request-Id"));
        assertNull(MDC.get("traceparent"));
    }

    @Test
    @DisplayName("3. RabbitMQ Topology: Khai báo đầy đủ Main Exchange, Dead-Letter Exchange (DLX) và 3 cặp Queue + DLQ")
    void testRabbitMqTopologyDeclarations() {
        EventPlatformMessagingConfig config = new EventPlatformMessagingConfig();
        TopicExchange mainExchange = config.eventPlatformExchange();
        DirectExchange dlxExchange = config.eventPlatformDeadLetterExchange();

        assertEquals(EventPlatformMessagingConfig.MAIN_EXCHANGE, mainExchange.getName());
        assertTrue(mainExchange.isDurable());
        assertEquals(EventPlatformMessagingConfig.DLX_EXCHANGE, dlxExchange.getName());

        Queue emailQueue = config.notificationEmailQueue();
        assertEquals(EventPlatformMessagingConfig.DLX_EXCHANGE, emailQueue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(EventPlatformMessagingConfig.RK_NOTIFICATION_EMAIL_DLQ,
                emailQueue.getArguments().get("x-dead-letter-routing-key"));

        Queue paymentQueue = config.paymentOrderQueue();
        assertEquals(EventPlatformMessagingConfig.DLX_EXCHANGE, paymentQueue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(EventPlatformMessagingConfig.RK_PAYMENT_ORDER_DLQ,
                paymentQueue.getArguments().get("x-dead-letter-routing-key"));

        Queue ticketQueue = config.ticketReservationQueue();
        assertEquals(EventPlatformMessagingConfig.DLX_EXCHANGE, ticketQueue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(EventPlatformMessagingConfig.RK_TICKET_RESERVATION_DLQ,
                ticketQueue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    @DisplayName("4. CommonEventPublisher & IdempotentConsumerTemplate: Gửi kèm TraceId, Chống trùng lặp & Đẩy DLQ sau 3 lần lỗi")
    void testPublisherIdempotencyAndDeadLetterRouting() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        CommonEventPublisher publisher = new CommonEventPublisher(rabbitTemplate);

        EventEnvelope<Map<String, Object>> published = publisher.publish(
                "notification.email.otp",
                "OTP_REQUESTED",
                Map.of("email", "user@event.vn", "otp", "123456"),
                "trace-abc-999"
        );

        assertNotNull(published.getEventId());
        assertEquals("OTP_REQUESTED", published.getEventType());
        assertEquals("trace-abc-999", published.getTraceId());
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(EventPlatformMessagingConfig.MAIN_EXCHANGE),
                eq("notification.email.otp"),
                eq(published),
                any(MessagePostProcessor.class)
        );

        // Kiểm thử IdempotentConsumerTemplate: Xử lý lần 1 -> true, gửi trùng lần 2 cùng eventId -> false (bỏ qua)
        IdempotentConsumerTemplate consumerTemplate = new IdempotentConsumerTemplate(null);
        AtomicInteger executionCount = new AtomicInteger(0);

        boolean firstRun = consumerTemplate.executeIdempotently("notification-service", published,
                env -> executionCount.incrementAndGet());
        boolean duplicateRun = consumerTemplate.executeIdempotently("notification-service", published,
                env -> executionCount.incrementAndGet());

        assertTrue(firstRun);
        assertFalse(duplicateRun);
        assertEquals(1, executionCount.get(), "Consumer chỉ được phép thực thi đúng 1 lần cho cùng một eventId!");

        // Kiểm thử Retry 3 lần thất bại -> Ném AmqpRejectAndDontRequeueException để đẩy thẳng sang *.dlq
        EventEnvelope<String> failingEvent = EventEnvelope.of("PAYMENT_FAILED_TEST", "order-101", "trace-retry");
        AtomicInteger retryCounter = new AtomicInteger(0);

        assertThrows(AmqpRejectAndDontRequeueException.class, () ->
                consumerTemplate.executeWithRetryAndDlq("payment-service", failingEvent, 3, env -> {
                    retryCounter.incrementAndGet();
                    throw new RuntimeException("Simulated transient timeout");
                })
        );
        assertEquals(3, retryCounter.get(), "Phải thử đúng 3 lần trước khi đẩy vào Dead-Letter Queue!");

        // Kiểm thử Poison Message (NonRetryableEventException) -> Không retry, đẩy sang DLQ ngay lập tức ở lần 1
        EventEnvelope<String> poisonEvent = EventEnvelope.of("POISON_PAYLOAD", "{corrupted}", "trace-poison");
        AtomicInteger poisonCounter = new AtomicInteger(0);

        assertThrows(AmqpRejectAndDontRequeueException.class, () ->
                consumerTemplate.executeWithRetryAndDlq("ticket-service", poisonEvent, 3, env -> {
                    poisonCounter.incrementAndGet();
                    throw new NonRetryableEventException("Invalid ticket reservation payload");
                })
        );
        assertEquals(1, poisonCounter.get(), "Poison message không được phép retry gây treo hàng đợi!");
    }
}
