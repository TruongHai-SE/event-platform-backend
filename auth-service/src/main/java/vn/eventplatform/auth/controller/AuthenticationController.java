package vn.eventplatform.auth.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.auth.dto.*;
import vn.eventplatform.auth.service.AuthenticationService;
import vn.eventplatform.common.dto.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @Value("${security.token.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${security.token.cookie-same-site:Lax}")
    private String cookieSameSite;

    @Value("${security.token.refresh-expiration-days:7}")
    private long refreshExpirationDays;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse data = authenticationService.register(request);
        return ResponseEntity.ok(ApiResponse.ok("Đăng ký thành công", data));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authenticationService.verifyEmail(request);
        return ResponseEntity.ok(ApiResponse.ok("Kích hoạt tài khoản thành công! Bạn có thể đăng nhập ngay bây giờ.", null));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authenticationService.resendVerification(request);
        return ResponseEntity.ok(ApiResponse.ok("Mã kích hoạt mới đã được gửi tới email của bạn.", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String clientIp = getClientIp(httpRequest);

        AuthenticationService.LoginResult result = authenticationService.login(request, userAgent, clientIp);
        setRefreshTokenCookie(httpResponse, result.getRawRefreshToken(), refreshExpirationDays * 24 * 60 * 60);

        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", result.getTokenResponse()));
    }

    @PostMapping("/oauth2/google")
    public ResponseEntity<ApiResponse<TokenResponse>> loginWithGoogle(
            @Valid @RequestBody GoogleOAuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String clientIp = getClientIp(httpRequest);

        AuthenticationService.LoginResult result = authenticationService.loginWithGoogle(request, userAgent, clientIp);
        setRefreshTokenCookie(httpResponse, result.getRawRefreshToken(), refreshExpirationDays * 24 * 60 * 60);

        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập Google thành công", result.getTokenResponse()));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
            @CookieValue(name = "refreshToken", required = false) String refreshTokenFromCookie,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String clientIp = getClientIp(httpRequest);

        AuthenticationService.LoginResult result = authenticationService.refreshToken(refreshTokenFromCookie, userAgent, clientIp);
        setRefreshTokenCookie(httpResponse, result.getRawRefreshToken(), refreshExpirationDays * 24 * 60 * 60);

        return ResponseEntity.ok(ApiResponse.ok("Làm mới phiên đăng nhập thành công", result.getTokenResponse()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshTokenFromCookie,
            HttpServletResponse httpResponse
    ) {
        authenticationService.logout(refreshTokenFromCookie);
        setRefreshTokenCookie(httpResponse, "", 0); // Xóa cookie

        return ResponseEntity.ok(ApiResponse.ok("Đăng xuất thành công", null));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String tokenValue, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", tokenValue)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite(cookieSameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
