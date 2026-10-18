package vn.eventplatform.payment.payment.strategy;

import vn.eventplatform.payment.domain.PaymentGateway;
import vn.eventplatform.payment.payment.PaymentContext;
import vn.eventplatform.payment.payment.dto.PaymentCallbackResult;
import vn.eventplatform.payment.payment.dto.PaymentInitResult;
import vn.eventplatform.payment.payment.dto.RefundContext;
import vn.eventplatform.payment.payment.dto.RefundResult;

import java.util.Map;

public interface PaymentStrategy {

    PaymentGateway getGateway();

    PaymentInitResult createPaymentUrl(PaymentContext context);

    PaymentCallbackResult verifyCallback(Map<String, String> callbackParams);

    RefundResult refund(RefundContext context);
}
