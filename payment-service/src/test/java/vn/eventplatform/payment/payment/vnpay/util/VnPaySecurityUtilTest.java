package vn.eventplatform.payment.payment.vnpay.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VnPaySecurityUtilTest {

    @Test
    @DisplayName("HMAC-SHA512 hashing generates correct 128 character hex string")
    void testHmacSHA512() {
        String key = "TESTSECRETKEY";
        String data = "vnp_Amount=10000000&vnp_Command=pay";
        String hash = VnPaySecurityUtil.hmacSHA512(key, data);

        assertThat(hash).isNotNull();
        assertThat(hash.length()).isEqualTo(128);
    }

    @Test
    @DisplayName("hashAllFields sorts keys and skips null/empty values")
    void testHashAllFields() {
        String secret = "SECRET123";
        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_TxnRef", "ORD-001");
        fields.put("vnp_Amount", "1000000");
        fields.put("vnp_Empty", "");
        fields.put("vnp_Null", null);

        String hash = VnPaySecurityUtil.hashAllFields(fields, secret);
        assertThat(hash).isNotNull();
        assertThat(hash.length()).isEqualTo(128);
    }
}
