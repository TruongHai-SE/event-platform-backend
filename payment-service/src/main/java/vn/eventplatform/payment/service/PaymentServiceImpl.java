package vn.eventplatform.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.payment.client.OrderServiceClient;
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

import vn.eventplatform.payment.payment.vnpay.config.VnPayProperties;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @Value("${security.internal.secret}")
    private String internalSecret;

    @Override
    @Transactional
    public PaymentInitResponse createPayment(CreatePaymentRequest request, String clientIp) {
        log.info("Khởi tạo thanh toán cho orderId: {}, method: {}", request.getOrderId(), request.getMethod());

        ApiResponse<InternalOrderResponse> orderResponse = orderServiceClient.getOrderById(internalSecret, request.getOrderId());
        if (orderResponse == null || !orderResponse.isSuccess() || orderResponse.getData() == null) {
            log.error("Không tìm thấy đơn hàng hoặc order-service lỗi: orderId={}", request.getOrderId());
            throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy thông tin đơn hàng hoặc dịch vụ đơn hàng không phản hồi");
        }

        InternalOrderResponse order = orderResponse.getData();
        if (!"PENDING".equalsIgnoreCase(order.getStatus())) {
            log.error("Đơn hàng không ở trạng thái PENDING: orderId={}, status={}", order.getId(), order.getStatus());
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn hàng không ở trạng thái chờ thanh toán (PENDING)");
        }

        Payment payment = Payment.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .method(request.getMethod())
                .amount(order.getTotalAmount())
                .status(PaymentStatus.PENDING)
                .build();
        Payment savedPayment = paymentRepository.save(payment);

        PaymentContext context = PaymentContext.builder()
                .paymentId(savedPayment.getId())
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .amount(order.getTotalAmount())
                .method(request.getMethod())
                .clientIp(clientIp)
                .returnUrl(vnPayProperties.getReturnUrl())
                .orderInfo("Thanh toan don hang " + order.getOrderCode())
                .expiresAt(order.getExpiresAt())
                .customerEmail(order.getCustomerEmail())
                .customerName(order.getCustomerName())
                .build();

        PaymentInitResult initResult = paymentStrategyFactory.getStrategy(request.getMethod()).createPaymentUrl(context);

        PaymentInitResponse response = paymentMapper.toInitResponse(savedPayment, initResult);
        response.setExpiresAt(order.getExpiresAt());
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
        Payment paymentToUpdate;
        if (!pendingPayments.isEmpty()) {
            paymentToUpdate = pendingPayments.get(0);
        } else {
            paymentToUpdate = paymentRepository.findByOrderCode(orderCode).orElse(null);
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

            paymentToUpdate.setStatus(PaymentStatus.SUCCESS);
            paymentToUpdate.setTransactionId(result.getTransactionId());
            paymentToUpdate.setPaidAt(result.getPayDate() != null ? result.getPayDate() : Instant.now());
            paymentRepository.save(paymentToUpdate);

            OrderMarkPaidRequest markPaidRequest = OrderMarkPaidRequest.builder()
                    .paymentId(paymentToUpdate.getId())
                    .transactionId(result.getTransactionId())
                    .paymentMethod(paymentToUpdate.getMethod().name())
                    .paidAt(paymentToUpdate.getPaidAt())
                    .build();

            try {
                ApiResponse<InternalOrderResponse> orderUpdateRes = orderServiceClient.markOrderPaid(
                        internalSecret, paymentToUpdate.getOrderId(), markPaidRequest);
                if (orderUpdateRes != null && orderUpdateRes.isSuccess()) {
                    log.info("Đã cập nhật trạng thái PAID cho đơn hàng: orderId={}", paymentToUpdate.getOrderId());
                } else {
                    log.warn("Gọi order-service markOrderPaid trả về không thành công: {}", orderUpdateRes);
                }
            } catch (Exception e) {
                log.error("Lỗi Feign khi gọi order-service markOrderPaid cho orderId={}", paymentToUpdate.getOrderId(), e);
            }
        } else {
            if (paymentToUpdate.getStatus() == PaymentStatus.PENDING) {
                paymentToUpdate.setStatus(PaymentStatus.FAILED);
                paymentToUpdate.setTransactionId(result.getTransactionId());
                paymentRepository.save(paymentToUpdate);
            }
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch thanh toán"));
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByOrderId(UUID orderId) {
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
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
}
