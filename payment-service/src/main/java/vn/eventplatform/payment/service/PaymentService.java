package vn.eventplatform.payment.service;

import vn.eventplatform.payment.dto.*;
import vn.eventplatform.payment.dto.sepay.SepayWebhookRequest;
import vn.eventplatform.payment.dto.sepay.SepayWebhookResponse;
import vn.eventplatform.payment.payment.dto.PaymentCallbackResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PaymentService {

    PaymentInitResponse createPayment(CreatePaymentRequest request, String clientIp);

    PaymentInitResponse createPayment(CreatePaymentRequest request, UUID currentUserId, boolean isAdminOrStaff, String clientIp);

    PaymentInitResponse retryPayment(InternalRetryPaymentRequest request);

    PaymentCallbackResult handleVnPayCallback(Map<String, String> allParams);

    SepayWebhookResponse handleSepayWebhook(String authHeader, SepayWebhookRequest request);

    PaymentResponse getPaymentById(UUID id);

    PaymentResponse getPaymentById(UUID id, UUID currentUserId, boolean isAdminOrStaff);

    List<PaymentResponse> getPaymentsByOrderId(UUID orderId);

    List<PaymentResponse> getPaymentsByOrderId(UUID orderId, UUID currentUserId, boolean isAdminOrStaff);

    List<PaymentResponse> getPaymentsByOrderCode(String orderCode, UUID currentUserId, boolean isAdminOrStaff);

    RefundResponse createRefund(CreateRefundRequest request);
}
