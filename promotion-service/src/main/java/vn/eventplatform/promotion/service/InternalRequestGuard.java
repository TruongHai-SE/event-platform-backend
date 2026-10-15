package vn.eventplatform.promotion.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalRequestGuard {
    private final byte[] secret;

    public InternalRequestGuard(@Value("${security.internal.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public void verify(String presentedSecret) {
        if (presentedSecret == null || !MessageDigest.isEqual(secret, presentedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Yêu cầu nội bộ không hợp lệ");
        }
    }
}
