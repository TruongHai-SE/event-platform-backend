package vn.eventplatform.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSender {

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://api.brevo.com/v3")
            .build();

    @Value("${security.brevo.api-key:}")
    private String brevoApiKey;

    @Value("${security.mail.from-email:no-reply@eventplatform.com}")
    private String fromEmail;

    @Value("${security.mail.from-name:Event & Festival Platform}")
    private String fromName;

    @Async
    public void sendVerificationEmail(String recipientEmail, String recipientName, String verificationToken) {
        log.info("Sending verification email via Brevo REST API to: {}", recipientEmail);

        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            log.warn("BREVO_API_KEY is not configured. Live email skipped. Verification code for {}: [{}]",
                    recipientEmail, verificationToken);
            return;
        }

        try {
            String htmlContent = """
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px;">
                        <h2 style="color: #4f46e5; text-align: center;">Chào mừng bạn đến với Event & Festival Platform!</h2>
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Cảm ơn bạn đã đăng ký tài khoản. Vui lòng sử dụng mã xác thực dưới đây để kích hoạt tài khoản của bạn:</p>
                        <div style="background-color: #f3f4f6; padding: 15px; text-align: center; border-radius: 6px; margin: 20px 0;">
                            <span style="font-size: 24px; font-weight: bold; letter-spacing: 4px; color: #1f2937;">%s</span>
                        </div>
                        <p style="color: #6b7280; font-size: 13px;">Mã xác thực có hiệu lực trong vòng 24 giờ. Nếu bạn không thực hiện đăng ký này, xin vui lòng bỏ qua email.</p>
                        <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 20px 0;" />
                        <p style="text-align: center; color: #9ca3af; font-size: 12px;">© 2026 Event & Festival Platform. All rights reserved.</p>
                    </div>
                    """.formatted(recipientName != null ? recipientName : recipientEmail, verificationToken);

            Map<String, Object> body = Map.of(
                    "sender", Map.of("email", fromEmail, "name", fromName),
                    "to", List.of(Map.of("email", recipientEmail, "name", recipientName != null ? recipientName : recipientEmail)),
                    "subject", "[Event & Festival Platform] Xác thực tài khoản của bạn",
                    "htmlContent", htmlContent
            );

            restClient.post()
                    .uri("/smtp/email")
                    .header("api-key", brevoApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Verification email sent successfully via Brevo REST API to {}", recipientEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email via Brevo REST API to {}: {}", recipientEmail, e.getMessage());
        }
    }
}
