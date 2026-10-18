package vn.eventplatform.payment.payment.sepay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.eventplatform.payment.domain.PaymentGateway;
import vn.eventplatform.payment.payment.PaymentContext;
import vn.eventplatform.payment.payment.dto.PaymentInitResult;
import vn.eventplatform.payment.payment.sepay.config.SePayProperties;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SePayPaymentStrategyTest {

    @Test
    @DisplayName("createPaymentUrl generates correct VietQR URL without hyphens")
    void createPaymentUrl_Success() {
        SePayProperties properties = new SePayProperties();
        properties.setAccountNumber("0000000001");
        properties.setBankName("MBBank");
        properties.setQrTemplate("compact");
        properties.setQrBaseUrl("https://qr.sepay.vn/img");

        SePayPaymentStrategy strategy = new SePayPaymentStrategy(properties);

        PaymentContext context = PaymentContext.builder()
                .paymentId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .orderCode("TKZ-20261018-ABC12")
                .amount(BigDecimal.valueOf(150000))
                .method(PaymentGateway.SEPAY)
                .build();

        PaymentInitResult result = strategy.createPaymentUrl(context);

        assertThat(result).isNotNull();
        assertThat(result.getMethod()).isEqualTo(PaymentGateway.SEPAY);
        assertThat(result.getQrCode()).contains("acc=0000000001");
        assertThat(result.getQrCode()).contains("bank=MBBank");
        assertThat(result.getQrCode()).contains("amount=150000");
        assertThat(result.getQrCode()).contains("des=TKZ20261018ABC12");
        assertThat(result.getQrCode()).doesNotContain("TKZ-20261018-ABC12");
    }

    @Test
    @DisplayName("validateApiKey checks key correctly")
    void validateApiKey_ChecksCorrectly() {
        SePayProperties properties = new SePayProperties();
        properties.setApiKey("sepay_secret_tkz_2026");

        assertThat(properties.validateApiKey("Apikey sepay_secret_tkz_2026")).isTrue();
        assertThat(properties.validateApiKey("Bearer sepay_secret_tkz_2026")).isTrue();
        assertThat(properties.validateApiKey("sepay_secret_tkz_2026")).isTrue();
        assertThat(properties.validateApiKey("Apikey wrong_key")).isFalse();
        assertThat(properties.validateApiKey(null)).isFalse();
    }
}
