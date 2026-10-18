package vn.eventplatform.payment.payment.sepay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "payment.sepay")
public class SePayProperties {

    private String apiKey;
    private String accountNumber;
    private String bankName;
    private String qrTemplate;
    private String qrBaseUrl;

    public boolean validateApiKey(String authHeader) {
        if (authHeader == null || authHeader.trim().isEmpty()) {
            return false;
        }
        // SePay gửi header dạng "Authorization: Apikey <API_KEY>" hoặc "Apikey <API_KEY>"
        String token = authHeader.replace("Apikey", "").replace("Bearer", "").trim();
        return apiKey != null && apiKey.equals(token);
    }
}
