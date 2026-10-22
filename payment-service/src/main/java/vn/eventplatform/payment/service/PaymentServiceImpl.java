package vn.eventplatform.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.common.messaging.EventPlatformMessagingConfig;
import vn.eventplatform.common.messaging.EventPlatformMessagingConfig.CommonEventPublisher;
import vn.eventplatform.common.messaging.event.OrderItemEventPayload;
import vn.eventplatform.common.messaging.event.PaymentFailedEventPayload;
import vn.eventplatform.common.messaging.event.PaymentSuccessEventPayload;
import vn.eventplatform.payment.client.OrderServiceClient;
import vn.eventplatform.payment.client.dto.InternalOrderItemResponse;
import vn.eventplatform.payment.client.dto.InternalOrderResponse;
import vn.eventplatform.payment.client.dto.OrderMarkPaidRequest;
import vn.eventplatform.payment.domain.*;
import vn.eventplatform.payment.dto.*;
import vn.eventplatform.payment.mapper.PaymentMapper;
import vn.eventplatform.payment.mapper.RefundLogMapper;
import vn.eventplatform.payment.payment.PaymentContext;
import vn.eventplatform.payment.payment.PaymentStrategyFactory;
import vn.eventplatform.payment.payment.dto.PaymentCallbackResult;
import vn.eventplatform.payment.payment.dto.PaymentInitResult;
import vn.eventplatform.payment.payment.dto.RefundContext;
import vn.eventplatform.payment.payment.dto.RefundResult;
import vn.eventplatform.payment.repository.PaymentRepository;
import vn.eventplatform.payment.repository.RefundLogRepository;

import vn.eventplatform.payment.dto.sepay.SepayWebhookRequest;
import vn.eventplatform.payment.dto.sepay.SepayWebhookResponse;
import vn.eventplatform.payment.payment.sepay.config.SePayProperties;
import vn.eventplatform.payment.payment.vnpay.config.VnPayProperties;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final RefundLogRepository refundLogRepository;
    private final PaymentStrategyFactory paymentStrategyFactory;
    private final PaymentMapper paymentMapper;
    private final RefundLogMapper refundLogMapper;
    private final OrderServiceClient orderServiceClient;
    private final VnPayProperties vnPayProperties;
    private final SePayProperties sePayProperties;

    @Autowired(required = false)
    private CommonEventPublisher eventPublisher;

    public void setEventPublisher(CommonEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Value("${security.internal.secret}")
    private String internalSecret;

    @Override
    @Transactional
    public PaymentInitResponse createPayment(CreatePaymentRequest request, String clientIp) {
        return createPayment(request, null, false, clientIp);
    }

    @Override
    @Transactional
    public PaymentInitResponse createPayment(CreatePaymentRequest request, UUID currentUserId, boolean isAdminOrStaff, String clientIp) {
        log.info("Khởi tạo thanh toán: orderCode={}, orderId={}, method={}, userId={}",
                request.getOrderCode(), request.getOrderId(), request.getMethod(), currentUserId);

        InternalOrderResponse order = fetchOrder(request);
        validateOrderAccess(order, currentUserId, isAdminOrStaff);

        if (!"PENDING".equalsIgnoreCase(order.getStatus())) {
            log.error("Đơn hàng không ở trạng thái PENDING: orderId={}, orderCode={}, status={}",
                    order.getId(), order.getOrderCode(), order.getStatus());
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn hàng không ở trạng thái chờ thanh toán (PENDING)");
        }

        return initPaymentForOrder(
                order.getId(),
                order.getOrderCode(),
                order.getTotalAmount(),
                request.getMethod(),
                order.getExpiresAt(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                clientIp
        );
    }

    private InternalOrderResponse fetchOrder(CreatePaymentRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Yêu cầu thanh toán không hợp lệ");
        }

        InternalOrderResponse order = null;
        if (request.getOrderCode() != null && !request.getOrderCode().trim().isEmpty()) {
            String trimmedCode = request.getOrderCode().trim();
            try {
                ApiResponse<InternalOrderResponse> orderResponse = orderServiceClient.getOrderByCode(internalSecret, trimmedCode);
                if (orderResponse != null && orderResponse.isSuccess() && orderResponse.getData() != null) {
                    order = orderResponse.getData();
                }
            } catch (Exception e) {
                log.error("Lỗi Feign khi gọi order-service getOrderByCode: orderCode={}", trimmedCode, e);
            }
        }

        if (order == null && request.getOrderId() != null) {
            try {
                ApiResponse<InternalOrderResponse> orderResponse = orderServiceClient.getOrderById(internalSecret, request.getOrderId());
                if (orderResponse != null && orderResponse.isSuccess() && orderResponse.getData() != null) {
                    order = orderResponse.getData();
                }
            } catch (Exception e) {
                log.error("Lỗi Feign khi gọi order-service getOrderById: orderId={}", request.getOrderId(), e);
            }
        }

        if (order == null) {
            log.error("Không tìm thấy đơn hàng hoặc order-service lỗi: orderCode={}, orderId={}",
                    request.getOrderCode(), request.getOrderId());
            throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy thông tin đơn hàng hoặc dịch vụ đơn hàng không phản hồi");
        }

        return order;
    }

    private void validateOrderAccess(InternalOrderResponse order, UUID currentUserId, boolean isAdminOrStaff) {
        if (currentUserId == null || isAdminOrStaff) {
            return;
        }
        if (order.getUserId() != null && !order.getUserId().equals(currentUserId)) {
            log.warn("IDOR Blocked: Người dùng userId={} không có quyền thao tác trên đơn hàng orderCode={}, ownerId={}",
                    currentUserId, order.getOrderCode(), order.getUserId());
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền thao tác trên đơn hàng này");
        }
    }

    @Override
    @Transactional
    public PaymentInitResponse retryPayment(InternalRetryPaymentRequest request) {
        log.info("Thử lại thanh toán (đổi cổng) cho orderId: {}, orderCode: {}, method: {}",
                request.getOrderId(), request.getOrderCode(), request.getMethod());

        return initPaymentForOrder(
                request.getOrderId(),
                request.getOrderCode(),
                request.getAmount(),
                request.getMethod(),
                request.getExpiresAt(),
                request.getCustomerName(),
                request.getCustomerEmail(),
                request.getClientIp()
        );
    }

    private PaymentInitResponse initPaymentForOrder(
            UUID orderId,
            String orderCode,
            BigDecimal amount,
            PaymentGateway method,
            Instant expiresAt,
            String customerName,
            String customerEmail,
            String clientIp
    ) {
        if (paymentRepository.existsByOrderIdAndStatusIn(orderId, List.of(PaymentStatus.SUCCESS))) {
            log.warn("Đơn hàng orderId={} đã có giao dịch thanh toán SUCCESS, từ chối tạo mới", orderId);
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn hàng này đã có giao dịch thanh toán thành công");
        }

        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            log.warn("Đơn hàng orderId={} đã hết hạn giữ chỗ thanh toán (expiresAt={})", orderId, expiresAt);
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn hàng đã hết thời gian giữ chỗ thanh toán");
        }

        List<Payment> pendingPayments = paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.PENDING);
        if (pendingPayments != null && !pendingPayments.isEmpty()) {
            for (Payment oldPayment : pendingPayments) {
                oldPayment.setStatus(PaymentStatus.CANCELLED);
            }
            paymentRepository.saveAll(pendingPayments);
            log.info("Đã hủy {} giao dịch thanh toán PENDING cũ của orderId={} trước khi khởi tạo cổng {}",
                    pendingPayments.size(), orderId, method);
        }

        Payment payment = Payment.builder()
                .orderId(orderId)
                .orderCode(orderCode)
                .method(method)
                .amount(amount)
                .status(PaymentStatus.PENDING)
                .build();
        Payment savedPayment = paymentRepository.save(payment);

        PaymentContext context = PaymentContext.builder()
                .paymentId(savedPayment.getId())
                .orderId(orderId)
                .orderCode(orderCode)
                .amount(amount)
                .method(method)
                .clientIp(clientIp)
                .returnUrl(vnPayProperties.getReturnUrl())
                .orderInfo("Thanh toan don hang " + orderCode)
                .expiresAt(expiresAt)
                .customerEmail(customerEmail)
                .customerName(customerName)
                .build();

        PaymentInitResult initResult = paymentStrategyFactory.getStrategy(method).createPaymentUrl(context);

        PaymentInitResponse response = paymentMapper.toInitResponse(savedPayment, initResult);
        response.setExpiresAt(expiresAt);
        return response;
    }

    @Override
    @Transactional
    public PaymentCallbackResult handleVnPayCallback(Map<String, String> allParams) {
        log.info("Xử lý VNPay callback: {}", allParams);

        PaymentCallbackResult result = paymentStrategyFactory.getStrategy(PaymentGateway.VNPAY).verifyCallback(allParams);

        String orderCode = result.getOrderCode();
        if (orderCode == null || orderCode.isEmpty()) {
            log.error("Callback VNPay thiếu orderCode / vnp_TxnRef");
            return result;
        }

        List<Payment> pendingPayments = paymentRepository.findByOrderCodeAndStatus(orderCode, PaymentStatus.PENDING);
        Payment paymentToUpdate = pendingPayments.stream()
                .filter(p -> p.getMethod() == null || p.getMethod() == PaymentGateway.VNPAY)
                .findFirst()
                .orElse(null);

        if (paymentToUpdate == null) {
            List<Payment> allOrderPayments = paymentRepository.findByOrderCodeOrderByCreatedAtDesc(orderCode);
            if (allOrderPayments != null && !allOrderPayments.isEmpty()) {
                paymentToUpdate = allOrderPayments.stream()
                        .filter(p -> p.getMethod() == null || p.getMethod() == PaymentGateway.VNPAY)
                        .findFirst()
                        .orElse(allOrderPayments.get(0));
            } else {
                paymentToUpdate = paymentRepository.findByOrderCode(orderCode).orElse(null);
            }
        }

        if (paymentToUpdate == null) {
            log.warn("Không tìm thấy giao dịch Payment tương ứng với orderCode={}", orderCode);
            return result;
        }

        result.setOrderId(paymentToUpdate.getOrderId());

        if (result.isSuccess()) {
            if (paymentToUpdate.getStatus() == PaymentStatus.SUCCESS) {
                log.info("Giao dịch Payment {} đã ở trạng thái SUCCESS (idempotent callback)", paymentToUpdate.getId());
                return result;
            }

            // Verify amount: Số tiền thanh toán VNPay phải khớp chính xác với số tiền đơn hàng
            if (result.getAmount() != null && result.getAmount().compareTo(paymentToUpdate.getAmount()) != 0) {
                log.error("Số tiền callback VNPay ({}) không khớp với số tiền đơn hàng ({}) cho paymentId={}",
                        result.getAmount(), paymentToUpdate.getAmount(), paymentToUpdate.getId());
                int updatedRows = paymentRepository.updatePaymentStatusIfMatch(
                        paymentToUpdate.getId(),
                        PaymentStatus.FAILED,
                        PaymentStatus.PENDING,
                        result.getTransactionId(),
                        null,
                        Instant.now()
                );
                if (updatedRows > 0) {
                    paymentToUpdate.setStatus(PaymentStatus.FAILED);
                    paymentToUpdate.setTransactionId(result.getTransactionId());
                    publishPaymentFailedEvent(paymentToUpdate, "Số tiền thanh toán VNPay không khớp với số tiền đơn hàng");
                }
                result.setSuccess(false);
                result.setMessage("Số tiền thanh toán VNPay không khớp với số tiền đơn hàng");
                return result;
            }

            Instant paidAt = result.getPayDate() != null ? result.getPayDate() : Instant.now();
            Instant now = Instant.now();
            int updatedRows = paymentRepository.updatePaymentStatusIfMatch(
                    paymentToUpdate.getId(),
                    PaymentStatus.SUCCESS,
                    PaymentStatus.PENDING,
                    result.getTransactionId(),
                    paidAt,
                    now
            );

            if (updatedRows == 0) {
                // Kiểm tra nếu luồng song song khác đã cập nhật sang SUCCESS trước đó
                Payment reloaded = paymentRepository.findById(paymentToUpdate.getId()).orElse(paymentToUpdate);
                if (reloaded.getStatus() == PaymentStatus.SUCCESS) {
                    log.info("Giao dịch Payment {} đã ở trạng thái SUCCESS bởi luồng khác (idempotent callback)", paymentToUpdate.getId());
                    return result;
                }
                log.warn("Không thể chuyển trạng thái Payment {} sang SUCCESS vì trạng thái hiện tại là {}",
                        paymentToUpdate.getId(), reloaded.getStatus());
                result.setSuccess(false);
                result.setMessage("Giao dịch thanh toán không còn ở trạng thái hợp lệ");
                return result;
            }

            paymentToUpdate.setStatus(PaymentStatus.SUCCESS);
            paymentToUpdate.setTransactionId(result.getTransactionId());
            paymentToUpdate.setPaidAt(paidAt);

            cancelOtherPendingPayments(paymentToUpdate.getOrderId(), paymentToUpdate.getId());

            OrderMarkPaidRequest markPaidRequest = OrderMarkPaidRequest.builder()
                    .paymentId(paymentToUpdate.getId())
                    .transactionId(result.getTransactionId())
                    .paymentMethod(paymentToUpdate.getMethod() != null ? paymentToUpdate.getMethod().name() : PaymentGateway.VNPAY.name())
                    .paidAt(paymentToUpdate.getPaidAt())
                    .build();

            InternalOrderResponse orderData = null;
            try {
                ApiResponse<InternalOrderResponse> orderUpdateRes = orderServiceClient.markOrderPaid(
                        internalSecret, paymentToUpdate.getOrderId(), markPaidRequest);
                if (orderUpdateRes != null && orderUpdateRes.isSuccess()) {
                    log.info("Đã cập nhật trạng thái PAID cho đơn hàng: orderId={}", paymentToUpdate.getOrderId());
                    orderData = orderUpdateRes.getData();
                } else {
                    log.warn("Gọi order-service markOrderPaid trả về không thành công: {}", orderUpdateRes);
                }
            } catch (Exception e) {
                log.error("Lỗi Feign khi gọi order-service markOrderPaid cho orderId={}", paymentToUpdate.getOrderId(), e);
            }

            if (orderData == null) {
                try {
                    ApiResponse<InternalOrderResponse> res = orderServiceClient.getOrderById(internalSecret, paymentToUpdate.getOrderId());
                    if (res != null && res.isSuccess()) {
                        orderData = res.getData();
                    }
                } catch (Exception e) {
                    log.warn("Không thể lấy thông tin chi tiết đơn hàng orderId={}", paymentToUpdate.getOrderId(), e);
                }
            }

            publishPaymentSuccessEvent(paymentToUpdate, orderData);
        } else {
            if (paymentToUpdate.getStatus() == PaymentStatus.PENDING
                    && (paymentToUpdate.getMethod() == null || paymentToUpdate.getMethod() == PaymentGateway.VNPAY)) {
                int updatedRows = paymentRepository.updatePaymentStatusIfMatch(
                        paymentToUpdate.getId(),
                        PaymentStatus.FAILED,
                        PaymentStatus.PENDING,
                        result.getTransactionId(),
                        null,
                        Instant.now()
                );
                if (updatedRows > 0) {
                    paymentToUpdate.setStatus(PaymentStatus.FAILED);
                    paymentToUpdate.setTransactionId(result.getTransactionId());
                    publishPaymentFailedEvent(paymentToUpdate, result.getMessage() != null ? result.getMessage() : "Thanh toán VNPay thất bại");
                }
            }
        }

        return result;
    }

    @Override
    @Transactional
    public SepayWebhookResponse handleSepayWebhook(String authHeader, SepayWebhookRequest request) {
        log.info("Xử lý Webhook SePay: id={}, code={}, amount={}, content={}",
                request.getId(), request.getCode(), request.getTransferAmount(), request.getContent());

        // 1. Xác thực API Key
        if (!sePayProperties.validateApiKey(authHeader)) {
            log.error("Xác thực API Key SePay thất bại: authHeader={}", authHeader);
            throw new AppException(ErrorCode.UNAUTHORIZED, "API Key SePay không hợp lệ");
        }

        // 2. Tìm mã thanh toán từ trường code hoặc từ content
        String rawCode = request.getCode();
        if (rawCode == null || rawCode.trim().isEmpty()) {
            rawCode = extractCodeFromContent(request.getContent());
        }

        if (rawCode == null || rawCode.trim().isEmpty()) {
            log.warn("SePay webhook không tìm thấy mã thanh toán: id={}, content={}", request.getId(), request.getContent());
            return SepayWebhookResponse.builder()
                    .success(true)
                    .message("Không tìm thấy mã thanh toán trong nội dung chuyển khoản")
                    .build();
        }

        // 3. Tìm giao dịch Payment tương ứng (bằng cách chuẩn hóa bỏ dấu '-')
        List<Payment> pendingPayments = paymentRepository.findByNormalizedOrderCodeAndStatus(rawCode, PaymentStatus.PENDING);
        Payment paymentToUpdate = pendingPayments.stream()
                .filter(p -> p.getMethod() == null || p.getMethod() == PaymentGateway.SEPAY)
                .findFirst()
                .orElseGet(() -> pendingPayments.isEmpty() ? null : pendingPayments.get(0));

        if (paymentToUpdate == null) {
            List<Payment> payments = paymentRepository.findByNormalizedOrderCode(rawCode);
            paymentToUpdate = payments.stream()
                    .filter(p -> p.getMethod() == null || p.getMethod() == PaymentGateway.SEPAY)
                    .findFirst()
                    .orElseGet(() -> payments.isEmpty() ? null : payments.get(0));
        }

        if (paymentToUpdate == null) {
            log.warn("Không tìm thấy giao dịch Payment tương ứng với code={}. Có thể là giao dịch test từ SePay.", rawCode);
            return SepayWebhookResponse.builder()
                    .success(true)
                    .message("Giao dịch hợp lệ nhưng không tìm thấy đơn hàng tương ứng (hoặc giao dịch test)")
                    .build();
        }

        // 4. Kiểm tra idempotent sơ bộ (nếu bản ghi đã ở trạng thái SUCCESS)
        if (paymentToUpdate.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Giao dịch Payment {} đã ở trạng thái SUCCESS (idempotent webhook)", paymentToUpdate.getId());
            return SepayWebhookResponse.builder()
                    .success(true)
                    .message("Giao dịch đã được xử lý trước đó")
                    .build();
        }

        // 5. Kiểm tra số tiền chuyển khoản
        if (request.getTransferAmount() != null && request.getTransferAmount().compareTo(paymentToUpdate.getAmount()) < 0) {
            log.warn("Số tiền chuyển khoản ({}) nhỏ hơn số tiền cần thanh toán ({}) cho paymentId={}",
                    request.getTransferAmount(), paymentToUpdate.getAmount(), paymentToUpdate.getId());
            publishPaymentFailedEvent(paymentToUpdate, "Số tiền chuyển khoản SePay không đủ");
            return SepayWebhookResponse.builder()
                    .success(true)
                    .message("Số tiền chuyển khoản không đủ")
                    .build();
        }

        // 6. Cập nhật trạng thái Payment sang SUCCESS bằng conditional update
        String transactionId = request.getReferenceCode() != null && !request.getReferenceCode().isEmpty()
                ? request.getReferenceCode()
                : (request.getId() != null ? String.valueOf(request.getId()) : "SEPAY-" + System.currentTimeMillis());

        Instant now = Instant.now();
        int updatedRows = paymentRepository.updatePaymentStatusIfMatch(
                paymentToUpdate.getId(),
                PaymentStatus.SUCCESS,
                PaymentStatus.PENDING,
                transactionId,
                now,
                now
        );

        if (updatedRows == 0) {
            Payment reloaded = paymentRepository.findById(paymentToUpdate.getId()).orElse(paymentToUpdate);
            if (reloaded.getStatus() == PaymentStatus.SUCCESS) {
                log.info("Giao dịch Payment {} đã ở trạng thái SUCCESS bởi luồng song song (idempotent webhook)", paymentToUpdate.getId());
                return SepayWebhookResponse.builder()
                        .success(true)
                        .message("Giao dịch đã được xử lý trước đó")
                        .build();
            }
            log.warn("Không thể cập nhật Payment {} sang SUCCESS vì trạng thái hiện tại là {}",
                    paymentToUpdate.getId(), reloaded.getStatus());
            return SepayWebhookResponse.builder()
                    .success(false)
                    .message("Giao dịch không còn ở trạng thái hợp lệ để thanh toán")
                    .build();
        }

        paymentToUpdate.setStatus(PaymentStatus.SUCCESS);
        paymentToUpdate.setTransactionId(transactionId);
        paymentToUpdate.setPaidAt(now);

        cancelOtherPendingPayments(paymentToUpdate.getOrderId(), paymentToUpdate.getId());

        // 7. Gọi order-service để chuyển trạng thái Order sang PAID
        OrderMarkPaidRequest markPaidRequest = OrderMarkPaidRequest.builder()
                .paymentId(paymentToUpdate.getId())
                .transactionId(transactionId)
                .paymentMethod(PaymentGateway.SEPAY.name())
                .paidAt(paymentToUpdate.getPaidAt())
                .build();

        InternalOrderResponse orderData = null;
        try {
            ApiResponse<InternalOrderResponse> orderUpdateRes = orderServiceClient.markOrderPaid(
                    internalSecret, paymentToUpdate.getOrderId(), markPaidRequest);
            if (orderUpdateRes != null && orderUpdateRes.isSuccess()) {
                log.info("Đã cập nhật trạng thái PAID cho đơn hàng từ SePay: orderId={}", paymentToUpdate.getOrderId());
                orderData = orderUpdateRes.getData();
            } else {
                log.warn("Gọi order-service markOrderPaid từ SePay trả về không thành công: {}", orderUpdateRes);
            }
        } catch (Exception e) {
            log.error("Lỗi Feign khi gọi order-service markOrderPaid cho orderId={}", paymentToUpdate.getOrderId(), e);
        }

        if (orderData == null) {
            try {
                ApiResponse<InternalOrderResponse> res = orderServiceClient.getOrderById(internalSecret, paymentToUpdate.getOrderId());
                if (res != null && res.isSuccess()) {
                    orderData = res.getData();
                }
            } catch (Exception e) {
                log.warn("Không thể lấy thông tin chi tiết đơn hàng orderId={}", paymentToUpdate.getOrderId(), e);
            }
        }

        publishPaymentSuccessEvent(paymentToUpdate, orderData);

        return SepayWebhookResponse.builder()
                .success(true)
                .message("Xử lý thanh toán thành công")
                .build();
    }

    private void cancelOtherPendingPayments(UUID orderId, UUID currentSuccessPaymentId) {
        if (orderId == null) {
            return;
        }
        int cancelled = paymentRepository.cancelOtherPendingPayments(
                orderId,
                currentSuccessPaymentId,
                PaymentStatus.CANCELLED,
                PaymentStatus.PENDING,
                Instant.now()
        );
        if (cancelled > 0) {
            log.info("Đã hủy {} giao dịch PENDING khác của orderId={}", cancelled, orderId);
        }
    }

    private String extractCodeFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        Pattern pattern = Pattern.compile("(?i)TKZ[A-Z0-9-]{6,25}");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID id) {
        return getPaymentById(id, null, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID id, UUID currentUserId, boolean isAdminOrStaff) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch thanh toán"));

        if (currentUserId != null && !isAdminOrStaff) {
            ApiResponse<InternalOrderResponse> orderResponse = orderServiceClient.getOrderById(internalSecret, payment.getOrderId());
            if (orderResponse != null && orderResponse.isSuccess() && orderResponse.getData() != null) {
                validateOrderAccess(orderResponse.getData(), currentUserId, isAdminOrStaff);
            }
        }

        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByOrderId(UUID orderId) {
        return getPaymentsByOrderId(orderId, null, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByOrderId(UUID orderId, UUID currentUserId, boolean isAdminOrStaff) {
        if (currentUserId != null && !isAdminOrStaff) {
            ApiResponse<InternalOrderResponse> orderResponse = orderServiceClient.getOrderById(internalSecret, orderId);
            if (orderResponse != null && orderResponse.isSuccess() && orderResponse.getData() != null) {
                validateOrderAccess(orderResponse.getData(), currentUserId, isAdminOrStaff);
            }
        }
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        return paymentMapper.toResponseList(payments);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByOrderCode(String orderCode, UUID currentUserId, boolean isAdminOrStaff) {
        if (orderCode == null || orderCode.trim().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã đơn hàng không được để trống");
        }
        String trimmedCode = orderCode.trim();
        if (currentUserId != null && !isAdminOrStaff) {
            ApiResponse<InternalOrderResponse> orderResponse = orderServiceClient.getOrderByCode(internalSecret, trimmedCode);
            if (orderResponse != null && orderResponse.isSuccess() && orderResponse.getData() != null) {
                validateOrderAccess(orderResponse.getData(), currentUserId, isAdminOrStaff);
            }
        }
        List<Payment> payments = paymentRepository.findByOrderCodeOrderByCreatedAtDesc(trimmedCode);
        return paymentMapper.toResponseList(payments);
    }

    @Override
    @Transactional
    public RefundResponse createRefund(CreateRefundRequest request) {
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch thanh toán gốc"));

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ có thể hoàn tiền cho giao dịch đã thanh toán thành công");
        }

        if (request.getAmount().compareTo(payment.getAmount()) > 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số tiền hoàn không được vượt quá số tiền thanh toán gốc");
        }

        RefundLog refundLog = RefundLog.builder()
                .orderId(payment.getOrderId())
                .payment(payment)
                .amount(request.getAmount())
                .reason(request.getReason())
                .status(RefundStatus.PROCESSING)
                .idempotencyKey(request.getIdempotencyKey())
                .build();
        RefundLog savedRefundLog = refundLogRepository.save(refundLog);

        RefundContext refundContext = RefundContext.builder()
                .refundLogId(savedRefundLog.getId())
                .orderId(payment.getOrderId())
                .orderCode(payment.getOrderCode())
                .originalTransactionId(payment.getTransactionId())
                .amount(request.getAmount())
                .reason(request.getReason())
                .idempotencyKey(request.getIdempotencyKey())
                .method(payment.getMethod())
                .build();

        RefundResult refundResult = paymentStrategyFactory.getStrategy(payment.getMethod()).refund(refundContext);

        savedRefundLog.setStatus(refundResult.getStatus());
        savedRefundLog.setGatewayRefundId(refundResult.getGatewayRefundId());
        savedRefundLog.setRefundedAt(refundResult.getRefundedAt());
        refundLogRepository.save(savedRefundLog);

        return refundLogMapper.toResponse(savedRefundLog);
    }

    @Value("${payment.scheduler.expiration-threshold-minutes:15}")
    private long expirationThresholdMinutes = 15;

    @Override
    @Transactional
    public int cancelExpiredPendingPayments() {
        Instant threshold = Instant.now().minus(expirationThresholdMinutes, ChronoUnit.MINUTES);
        List<Payment> expiredPayments = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold);
        if (expiredPayments == null || expiredPayments.isEmpty()) {
            return 0;
        }

        int count = 0;
        Instant now = Instant.now();
        for (Payment payment : expiredPayments) {
            try {
                int updatedRows = paymentRepository.updatePaymentStatusIfMatch(
                        payment.getId(),
                        PaymentStatus.CANCELLED,
                        PaymentStatus.PENDING,
                        payment.getTransactionId(),
                        null,
                        now
                );
                if (updatedRows > 0) {
                    payment.setStatus(PaymentStatus.CANCELLED);
                    log.info("Đã hủy giao dịch paymentId={} do quá hạn {} phút (orderCode={})",
                            payment.getId(), expirationThresholdMinutes, payment.getOrderCode());
                    publishPaymentFailedEvent(payment, "Giao dịch thanh toán quá hạn " + expirationThresholdMinutes + " phút");
                    count++;
                } else {
                    log.debug("Payment {} không còn ở trạng thái PENDING, bỏ qua hủy quá hạn", payment.getId());
                }
            } catch (Exception e) {
                log.error("Lỗi khi hủy giao dịch thanh toán quá hạn paymentId={}", payment.getId(), e);
            }
        }
        return count;
    }

    private void publishPaymentFailedEvent(Payment payment, String reason) {
        if (eventPublisher == null || payment == null) {
            return;
        }
        try {
            PaymentFailedEventPayload payload = PaymentFailedEventPayload.builder()
                    .paymentId(payment.getId())
                    .orderId(payment.getOrderId())
                    .orderCode(payment.getOrderCode())
                    .amount(payment.getAmount())
                    .paymentMethod(payment.getMethod() != null ? payment.getMethod().name() : null)
                    .transactionId(payment.getTransactionId())
                    .failureReason(reason)
                    .failedAt(Instant.now())
                    .build();

            eventPublisher.publish(
                    EventPlatformMessagingConfig.RK_PAYMENT_ORDER_FAILED,
                    EventPlatformMessagingConfig.EVENT_TYPE_PAYMENT_FAILED,
                    payload
            );
        } catch (Exception e) {
            log.error("Lỗi khi publish event payment.failed cho paymentId={}", payment.getId(), e);
        }
    }

    private void publishPaymentSuccessEvent(Payment payment, InternalOrderResponse order) {
        if (eventPublisher == null || payment == null) {
            return;
        }
        try {
            List<OrderItemEventPayload> itemPayloads = order != null && order.getItems() != null
                    ? order.getItems().stream()
                    .map(item -> OrderItemEventPayload.builder()
                            .ticketTypeId(item.getTicketTypeId())
                            .ticketTypeName(item.getTicketTypeName())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .subtotal(item.getSubtotal())
                            .build())
                    .toList()
                    : List.of();

            PaymentSuccessEventPayload payload = PaymentSuccessEventPayload.builder()
                    .paymentId(payment.getId())
                    .orderId(payment.getOrderId())
                    .orderCode(payment.getOrderCode() != null ? payment.getOrderCode() : (order != null ? order.getOrderCode() : null))
                    .userId(order != null ? order.getUserId() : null)
                    .eventId(order != null ? order.getEventId() : null)
                    .showTimeId(order != null ? order.getShowTimeId() : null)
                    .amount(payment.getAmount())
                    .paymentMethod(payment.getMethod() != null ? payment.getMethod().name() : null)
                    .transactionId(payment.getTransactionId())
                    .paidAt(payment.getPaidAt() != null ? payment.getPaidAt() : Instant.now())
                    .customerName(order != null ? order.getCustomerName() : null)
                    .customerEmail(order != null ? order.getCustomerEmail() : null)
                    .items(itemPayloads)
                    .build();

            eventPublisher.publish(
                    EventPlatformMessagingConfig.RK_PAYMENT_ORDER_SUCCESS,
                    EventPlatformMessagingConfig.EVENT_TYPE_PAYMENT_SUCCESS,
                    payload
            );
            log.info("Đã publish event payment.success cho paymentId={}, orderId={}", payment.getId(), payment.getOrderId());
        } catch (Exception e) {
            log.error("Lỗi khi publish event payment.success cho paymentId={}", payment.getId(), e);
        }
    }
}
