package vn.eventplatform.payment.payment.vnpay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.eventplatform.payment.domain.PaymentGateway;
import vn.eventplatform.payment.payment.PaymentContext;
import vn.eventplatform.payment.payment.dto.PaymentCallbackResult;
import vn.eventplatform.payment.payment.dto.PaymentInitResult;
import vn.eventplatform.payment.payment.vnpay.config.VnPayProperties;
import vn.eventplatform.payment.payment.vnpay.util.VnPaySecurityUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VnPayPaymentStrategyTest {

    private VnPayPaymentStrategy strategy;
    private VnPayProperties properties;

    @BeforeEach
    void setUp() {
        properties = new VnPayProperties();
        properties.setTmnCode("TESTTMN");
        properties.setHashSecret("TESTSECRET1234567890123456789012");
        properties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("http://localhost:5173/payment/vnpay-callback");
        properties.setApiUrl("https://sandbox.vnpayment.vn/merchant_webapi/api/transaction");

        strategy = new VnPayPaymentStrategy(properties);
    }

    @Test
    @DisplayName("getGateway returns VNPAY")
    void testGetGateway() {
        assertThat(strategy.getGateway()).isEqualTo(PaymentGateway.VNPAY);
    }

    @Test
    @DisplayName("createPaymentUrl generates valid VNPay payment URL with secure hash")
    void testCreatePaymentUrl() {
        PaymentContext context = PaymentContext.builder()
                .paymentId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .orderCode("TKZ-20261018-001")
                .amount(new BigDecimal("150000.00"))
                .method(PaymentGateway.VNPAY)
                .clientIp("127.0.0.1")
                .returnUrl("http://localhost:5173/callback")
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        PaymentInitResult result = strategy.createPaymentUrl(context);

        assertThat(result).isNotNull();
        assertThat(result.getMethod()).isEqualTo(PaymentGateway.VNPAY);
        assertThat(result.getPaymentUrl()).contains("vnp_TmnCode=TESTTMN");
        assertThat(result.getPaymentUrl()).contains("vnp_Amount=15000000");
        assertThat(result.getPaymentUrl()).contains("vnp_SecureHash=");
    }

    @Test
    @DisplayName("verifyCallback succeeds with valid checksum and 00 response code")
    void testVerifyCallbackSuccess() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", "TESTTMN");
        params.put("vnp_Amount", "15000000");
        params.put("vnp_TxnRef", "TKZ-20261018-001");
        params.put("vnp_TransactionNo", "14000123");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_PayDate", "20261018103000");

        String hash = VnPaySecurityUtil.hashAllFields(params, properties.getHashSecret());
        params.put("vnp_SecureHash", hash);

        PaymentCallbackResult result = strategy.verifyCallback(params);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTransactionId()).isEqualTo("14000123");
        assertThat(result.getOrderCode()).isEqualTo("TKZ-20261018-001");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("150000.00"));
    }
}
