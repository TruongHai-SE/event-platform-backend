package vn.eventplatform.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
public class EventPlatformMessagingConfig {

    public static final String MAIN_EXCHANGE = "event.platform.exchange";
    public static final String DLX_EXCHANGE = "event.platform.dlx";

    public static final String QUEUE_NOTIFICATION_EMAIL = "q.notification.email";
    public static final String QUEUE_NOTIFICATION_EMAIL_DLQ = "q.notification.email.dlq";
    public static final String RK_NOTIFICATION_EMAIL = "notification.email.*";
    public static final String RK_NOTIFICATION_EMAIL_DLQ = "dlq.notification.email";

    public static final String QUEUE_PAYMENT_ORDER = "q.payment.order";
    public static final String QUEUE_PAYMENT_ORDER_DLQ = "q.payment.order.dlq";
    public static final String RK_PAYMENT_ORDER = "payment.order.*";
    public static final String RK_PAYMENT_ORDER_DLQ = "dlq.payment.order";

    public static final String QUEUE_TICKET_RESERVATION = "q.ticket.reservation";
    public static final String QUEUE_TICKET_RESERVATION_DLQ = "q.ticket.reservation.dlq";
    public static final String RK_TICKET_RESERVATION = "ticket.reservation.*";
    public static final String RK_TICKET_RESERVATION_DLQ = "dlq.ticket.reservation";

    public static final String QUEUE_ORDER_EVENT_CANCELLED = "q.order.event.cancelled";
    public static final String QUEUE_ORDER_EVENT_CANCELLED_DLQ = "q.order.event.cancelled.dlq";
    public static final String RK_EVENT_CANCELLED = "event.lifecycle.cancelled";
    public static final String RK_EVENT_CANCELLED_DLQ = "dlq.order.event.cancelled";

    public static final String RK_PAYMENT_ORDER_CREATED = "payment.order.created";
    public static final String RK_PAYMENT_ORDER_PAID = "payment.order.paid";
    public static final String RK_PAYMENT_ORDER_SUCCESS = "payment.order.success";
    public static final String RK_PAYMENT_ORDER_EXPIRED = "payment.order.expired";
    public static final String RK_PAYMENT_ORDER_FAILED = "payment.order.failed";

    public static final String EVENT_TYPE_ORDER_CREATED = "order.created";
    public static final String EVENT_TYPE_ORDER_PAID = "order.paid";
    public static final String EVENT_TYPE_PAYMENT_SUCCESS = "payment.success";
    public static final String EVENT_TYPE_ORDER_EXPIRED = "order.expired";
    public static final String EVENT_TYPE_PAYMENT_FAILED = "payment.failed";
    public static final String EVENT_TYPE_EVENT_CANCELLED = "event.cancelled";

    @Bean
    public TopicExchange eventPlatformExchange() {
        return ExchangeBuilder.topicExchange(MAIN_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange eventPlatformDeadLetterExchange() {
        return ExchangeBuilder.directExchange(DLX_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue notificationEmailQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATION_EMAIL)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RK_NOTIFICATION_EMAIL_DLQ)
                .build();
    }

    @Bean
    public Queue notificationEmailDlq() {
        return QueueBuilder.durable(QUEUE_NOTIFICATION_EMAIL_DLQ).build();
    }

    @Bean
    public Binding notificationEmailBinding(Queue notificationEmailQueue, TopicExchange eventPlatformExchange) {
        return BindingBuilder.bind(notificationEmailQueue).to(eventPlatformExchange).with(RK_NOTIFICATION_EMAIL);
    }

    @Bean
    public Binding notificationEmailDlqBinding(Queue notificationEmailDlq, DirectExchange eventPlatformDeadLetterExchange) {
        return BindingBuilder.bind(notificationEmailDlq).to(eventPlatformDeadLetterExchange).with(RK_NOTIFICATION_EMAIL_DLQ);
    }

    @Bean
    public Queue paymentOrderQueue() {
        return QueueBuilder.durable(QUEUE_PAYMENT_ORDER)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RK_PAYMENT_ORDER_DLQ)
                .build();
    }

    @Bean
    public Queue paymentOrderDlq() {
        return QueueBuilder.durable(QUEUE_PAYMENT_ORDER_DLQ).build();
    }

    @Bean
    public Binding paymentOrderBinding(Queue paymentOrderQueue, TopicExchange eventPlatformExchange) {
        return BindingBuilder.bind(paymentOrderQueue).to(eventPlatformExchange).with(RK_PAYMENT_ORDER);
    }

    @Bean
    public Binding paymentOrderDlqBinding(Queue paymentOrderDlq, DirectExchange eventPlatformDeadLetterExchange) {
        return BindingBuilder.bind(paymentOrderDlq).to(eventPlatformDeadLetterExchange).with(RK_PAYMENT_ORDER_DLQ);
    }

    @Bean
    public Queue ticketReservationQueue() {
        return QueueBuilder.durable(QUEUE_TICKET_RESERVATION)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RK_TICKET_RESERVATION_DLQ)
                .build();
    }

    @Bean
    public Queue ticketReservationDlq() {
        return QueueBuilder.durable(QUEUE_TICKET_RESERVATION_DLQ).build();
    }

    @Bean
    public Binding ticketReservationBinding(Queue ticketReservationQueue, TopicExchange eventPlatformExchange) {
        return BindingBuilder.bind(ticketReservationQueue).to(eventPlatformExchange).with(RK_TICKET_RESERVATION);
    }

    @Bean
    public Binding ticketReservationPaymentSuccessBinding(Queue ticketReservationQueue, TopicExchange eventPlatformExchange) {
        return BindingBuilder.bind(ticketReservationQueue).to(eventPlatformExchange).with(RK_PAYMENT_ORDER_SUCCESS);
    }

    @Bean
    public Binding ticketReservationOrderPaidBinding(Queue ticketReservationQueue, TopicExchange eventPlatformExchange) {
        return BindingBuilder.bind(ticketReservationQueue).to(eventPlatformExchange).with(RK_PAYMENT_ORDER_PAID);
    }

    @Bean
    public Binding ticketReservationDlqBinding(Queue ticketReservationDlq, DirectExchange eventPlatformDeadLetterExchange) {
        return BindingBuilder.bind(ticketReservationDlq).to(eventPlatformDeadLetterExchange).with(RK_TICKET_RESERVATION_DLQ);
    }

    @Bean
    public Queue orderEventCancelledQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_EVENT_CANCELLED)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RK_EVENT_CANCELLED_DLQ)
                .build();
    }

    @Bean
    public Queue orderEventCancelledDlq() {
        return QueueBuilder.durable(QUEUE_ORDER_EVENT_CANCELLED_DLQ).build();
    }

    @Bean
    public Binding orderEventCancelledBinding(Queue orderEventCancelledQueue, TopicExchange eventPlatformExchange) {
        return BindingBuilder.bind(orderEventCancelledQueue).to(eventPlatformExchange).with(RK_EVENT_CANCELLED);
    }

    @Bean
    public Binding orderEventCancelledDlqBinding(Queue orderEventCancelledDlq, DirectExchange eventPlatformDeadLetterExchange) {
        return BindingBuilder.bind(orderEventCancelledDlq).to(eventPlatformDeadLetterExchange).with(RK_EVENT_CANCELLED_DLQ);
    }

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(RabbitTemplate.class)
    public RabbitTemplate eventPlatformRabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setExchange(MAIN_EXCHANGE);
        return template;
    }

    @Bean
    public RetryOperationsInterceptor eventConsumerRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000L, 2.0, 4000L)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public CommonEventPublisher commonEventPublisher(RabbitTemplate rabbitTemplate) {
        return new CommonEventPublisher(rabbitTemplate);
    }

    @Bean
    public IdempotentConsumerTemplate idempotentConsumerTemplate(
            @Autowired(required = false) StringRedisTemplate redisTemplate) {
        return new IdempotentConsumerTemplate(redisTemplate);
    }

    @Bean
    public org.springframework.boot.ApplicationRunner rabbitTopologyInitializer(
            @Autowired(required = false) ConnectionFactory connectionFactory) {
        return args -> {
            if (connectionFactory != null) {
                try {
                    org.springframework.amqp.rabbit.core.RabbitAdmin admin =
                            new org.springframework.amqp.rabbit.core.RabbitAdmin(connectionFactory);
                    TopicExchange mainEx = eventPlatformExchange();
                    DirectExchange dlxEx = eventPlatformDeadLetterExchange();
                    Queue qEmail = notificationEmailQueue();
                    Queue qEmailDlq = notificationEmailDlq();
                    Queue qPayment = paymentOrderQueue();
                    Queue qPaymentDlq = paymentOrderDlq();
                    Queue qTicket = ticketReservationQueue();
                    Queue qTicketDlq = ticketReservationDlq();
                    Queue qOrderEventCancelled = orderEventCancelledQueue();
                    Queue qOrderEventCancelledDlq = orderEventCancelledDlq();

                    admin.declareExchange(mainEx);
                    admin.declareExchange(dlxEx);
                    admin.declareQueue(qEmail);
                    admin.declareQueue(qEmailDlq);
                    admin.declareQueue(qPayment);
                    admin.declareQueue(qPaymentDlq);
                    admin.declareQueue(qTicket);
                    admin.declareQueue(qTicketDlq);
                    admin.declareQueue(qOrderEventCancelled);
                    admin.declareQueue(qOrderEventCancelledDlq);
                    admin.declareBinding(notificationEmailBinding(qEmail, mainEx));
                    admin.declareBinding(notificationEmailDlqBinding(qEmailDlq, dlxEx));
                    admin.declareBinding(paymentOrderBinding(qPayment, mainEx));
                    admin.declareBinding(paymentOrderDlqBinding(qPaymentDlq, dlxEx));
                    admin.declareBinding(ticketReservationBinding(qTicket, mainEx));
                    admin.declareBinding(ticketReservationPaymentSuccessBinding(qTicket, mainEx));
                    admin.declareBinding(ticketReservationOrderPaidBinding(qTicket, mainEx));
                    admin.declareBinding(ticketReservationDlqBinding(qTicketDlq, dlxEx));
                    admin.declareBinding(orderEventCancelledBinding(qOrderEventCancelled, mainEx));
                    admin.declareBinding(orderEventCancelledDlqBinding(qOrderEventCancelledDlq, dlxEx));
                    log.info("Initialized RabbitMQ topology: exchange={}, dlx={}", MAIN_EXCHANGE, DLX_EXCHANGE);
                } catch (Exception ex) {
                    log.warn("Deferred RabbitMQ topology initialization: {}", ex.getMessage());
                }
            }
        };
    }

    public static class NonRetryableEventException extends RuntimeException {
        public NonRetryableEventException(String message) {
            super(message);
        }

        public NonRetryableEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class CommonEventPublisher {
        private final RabbitTemplate rabbitTemplate;

        public CommonEventPublisher(RabbitTemplate rabbitTemplate) {
            this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate must not be null");
        }

        public <T> EventEnvelope<T> publish(String routingKey, String eventType, T payload) {
            String mdcTraceId = MDC.get("requestId");
            return publish(routingKey, eventType, payload, mdcTraceId);
        }

        public <T> EventEnvelope<T> publish(String routingKey, String eventType, T payload, String traceId) {
            if (routingKey == null || routingKey.isBlank()) {
                throw new IllegalArgumentException("routingKey must not be blank");
            }
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("eventType must not be blank");
            }
            EventEnvelope<T> envelope = EventEnvelope.of(eventType, payload, traceId);
            rabbitTemplate.convertAndSend(MAIN_EXCHANGE, routingKey, envelope, message -> {
                message.getMessageProperties().setMessageId(envelope.getEventId().toString());
                message.getMessageProperties().setHeader("X-Request-Id", envelope.getTraceId());
                message.getMessageProperties().setHeader("X-Event-Type", envelope.getEventType());
                return message;
            });
            log.info("Published event [type={}, id={}, routingKey={}, traceId={}]",
                    envelope.getEventType(), envelope.getEventId(), routingKey, envelope.getTraceId());
            return envelope;
        }
    }

    public static class IdempotentConsumerTemplate {
        private static final String KEY_PREFIX = "processed_msg:";
        private static final Duration DEFAULT_TTL = Duration.ofHours(24);

        private final StringRedisTemplate redisTemplate;
        private final Map<String, Instant> inMemoryProcessedKeys = new ConcurrentHashMap<>();

        public IdempotentConsumerTemplate(StringRedisTemplate redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        public <T> boolean executeIdempotently(
                String consumerGroup,
                EventEnvelope<T> envelope,
                Consumer<EventEnvelope<T>> handler) {
            return executeWithRetryAndDlq(consumerGroup, envelope, 1, handler);
        }

        public <T> boolean executeWithRetryAndDlq(
                String consumerGroup,
                EventEnvelope<T> envelope,
                int maxAttempts,
                Consumer<EventEnvelope<T>> handler) {
            if (envelope == null || envelope.getEventId() == null) {
                throw new AmqpRejectAndDontRequeueException("Poison message: missing EventEnvelope or eventId");
            }
            if (consumerGroup == null || consumerGroup.isBlank()) {
                throw new IllegalArgumentException("consumerGroup must not be blank");
            }

            String dedupKey = KEY_PREFIX + consumerGroup.trim() + ":" + envelope.getEventId();
            if (!acquireIdempotencyLock(dedupKey)) {
                log.info("Skipping duplicate message [consumerGroup={}, eventId={}, eventType={}]",
                        consumerGroup, envelope.getEventId(), envelope.getEventType());
                return false;
            }

            int attempts = 0;
            int effectiveMax = Math.max(1, maxAttempts);
            while (attempts < effectiveMax) {
                attempts++;
                try {
                    handler.accept(envelope);
                    return true;
                } catch (NonRetryableEventException | MessageConversionException ex) {
                    log.error("Non-retryable poison message [consumerGroup={}, eventId={}]: {}",
                            consumerGroup, envelope.getEventId(), ex.getMessage());
                    throw new AmqpRejectAndDontRequeueException("Non-retryable message routed to DLQ", ex);
                } catch (Exception ex) {
                    if (attempts >= effectiveMax) {
                        releaseIdempotencyLock(dedupKey);
                        log.error("Message failed after {} attempts [consumerGroup={}, eventId={}], routing to DLQ",
                                attempts, consumerGroup, envelope.getEventId(), ex);
                        throw new AmqpRejectAndDontRequeueException(
                                "Exhausted " + attempts + " retry attempts, routing to DLQ", ex);
                    }
                    log.warn("Transient failure on attempt {}/{} for eventId={}, retrying...",
                            attempts, effectiveMax, envelope.getEventId());
                }
            }
            return false;
        }

        private boolean acquireIdempotencyLock(String key) {
            if (redisTemplate != null) {
                try {
                    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", DEFAULT_TTL);
                    if (acquired != null) {
                        return acquired;
                    }
                } catch (Exception ex) {
                    log.warn("Redis unavailable for idempotency check, falling back to in-memory lock: {}", ex.getMessage());
                }
            }
            Instant now = Instant.now();
            inMemoryProcessedKeys.entrySet().removeIf(e -> e.getValue().isBefore(now));
            return inMemoryProcessedKeys.putIfAbsent(key, now.plus(DEFAULT_TTL)) == null;
        }

        private void releaseIdempotencyLock(String key) {
            if (redisTemplate != null) {
                try {
                    redisTemplate.delete(key);
                } catch (Exception ignored) {
                }
            }
            inMemoryProcessedKeys.remove(key);
        }
    }
}
