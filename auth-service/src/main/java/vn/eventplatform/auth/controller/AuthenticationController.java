package vn.eventplatform.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.auth.dto.*;
import vn.eventplatform.auth.service.AuthenticationService;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;

import java.util.Map;
import java.util.UUID;

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

    @PostMapping({"/verify-email", "/verify"})
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

    @PostMapping({"/oauth2/google", "/google"})
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

    @PostMapping({"/refresh-token", "/refresh"})
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
            @CookieValue(name = "refreshToken", required = false) String refreshTokenFromCookie,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String rawRefreshToken = refreshTokenFromCookie;
        if ((rawRefreshToken == null || rawRefreshToken.isBlank()) && body != null) {
            rawRefreshToken = body.get("refreshToken");
        }
        String userAgent = httpRequest.getHeader("User-Agent");
        String clientIp = getClientIp(httpRequest);

        AuthenticationService.LoginResult result = authenticationService.refreshToken(rawRefreshToken, userAgent, clientIp);
        setRefreshTokenCookie(httpResponse, result.getRawRefreshToken(), refreshExpirationDays * 24 * 60 * 60);

        return ResponseEntity.ok(ApiResponse.ok("Làm mới phiên đăng nhập thành công", result.getTokenResponse()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshTokenFromCookie,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            HttpServletResponse httpResponse
    ) {
        authenticationService.logout(refreshTokenFromCookie, authorizationHeader);
        setRefreshTokenCookie(httpResponse, "", 0);

        return ResponseEntity.ok(ApiResponse.ok("Đăng xuất thành công", null));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            Authentication authentication,
            @CookieValue(name = "refreshToken", required = false) String refreshTokenFromCookie,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            HttpServletResponse httpResponse
    ) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID accountId = UUID.fromString(authentication.getPrincipal().toString());
        authenticationService.logoutAll(accountId, refreshTokenFromCookie, authorizationHeader);
        setRefreshTokenCookie(httpResponse, "", 0);

        return ResponseEntity.ok(ApiResponse.ok("Đã đăng xuất và thu hồi toàn bộ phiên đăng nhập trên mọi thiết bị", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authenticationService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Mã OTP đặt lại mật khẩu đã được gửi tới email của bạn.", null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authenticationService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Đặt lại mật khẩu thành công! Vui lòng đăng nhập lại.", null));
    }

    @PostMapping("/request-unlock")
    public ResponseEntity<ApiResponse<Void>> requestUnlockOtp(@Valid @RequestBody ForgotPasswordRequest request) {
        authenticationService.requestUnlockOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("Mã OTP mở khóa tài khoản đã được gửi tới email của bạn.", null));
    }

    @PostMapping("/unlock-account")
    public ResponseEntity<ApiResponse<Void>> unlockAccount(@Valid @RequestBody UnlockAccountRequest request) {
        authenticationService.unlockAccount(request);
        return ResponseEntity.ok(ApiResponse.ok("Mở khóa tài khoản thành công! Bạn có thể đăng nhập ngay bây giờ.", null));
    }

    @GetMapping("/security-policy")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SecurityPolicyDto>> getSecurityPolicy() {
        return ResponseEntity.ok(ApiResponse.ok(authenticationService.getSecurityPolicy()));
    }

    @PutMapping("/security-policy")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SecurityPolicyDto>> updateSecurityPolicy(
            Authentication authentication,
            @Valid @RequestBody SecurityPolicyDto request
    ) {
        UUID adminId = UUID.fromString(authentication.getPrincipal().toString());
        SecurityPolicyDto updated = authenticationService.updateSecurityPolicy(request, adminId);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật chính sách bảo mật thành công", updated));
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
