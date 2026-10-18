package vn.eventplatform.payment.service;

import vn.eventplatform.payment.dto.*;
import vn.eventplatform.payment.payment.dto.PaymentCallbackResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PaymentService {

    PaymentInitResponse createPayment(CreatePaymentRequest request, String clientIp);

    PaymentCallbackResult handleVnPayCallback(Map<String, String> allParams);

    PaymentResponse getPaymentById(UUID id);

    List<PaymentResponse> getPaymentsByOrderId(UUID orderId);

    RefundResponse createRefund(CreateRefundRequest request);
}
