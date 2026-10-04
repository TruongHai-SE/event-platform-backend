package vn.eventplatform.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;

import java.util.Collections;

@Slf4j
@Service
public class FederatedIdentityService {

    private final GoogleIdTokenVerifier verifier;

    public FederatedIdentityService(@Value("${security.oauth2.google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GooglePrincipal verifyGoogleIdToken(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new AppException(ErrorCode.TOKEN_INVALID, "Google ID Token không hợp lệ hoặc đã hết hạn");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");
            boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());

            if (!emailVerified) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Tài khoản Google chưa được xác thực email");
            }

            return new GooglePrincipal(email, name, pictureUrl);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google token verification failed: {}", e.getMessage());
            throw new AppException(ErrorCode.TOKEN_INVALID, "Không thể xác thực Google ID Token: " + e.getMessage());
        }
    }

    public record GooglePrincipal(String email, String name, String pictureUrl) {}
}
