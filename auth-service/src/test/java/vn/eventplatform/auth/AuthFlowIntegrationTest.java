package vn.eventplatform.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.eventplatform.auth.client.UserProfilePort;
import vn.eventplatform.auth.domain.*;
import vn.eventplatform.auth.dto.*;
import vn.eventplatform.auth.repository.AccountRepository;
import vn.eventplatform.auth.repository.VerificationTokenRepository;
import vn.eventplatform.auth.service.FederatedIdentityService;
import vn.eventplatform.auth.service.NotificationSender;
import vn.eventplatform.auth.service.PasswordEncryptor;
import vn.eventplatform.auth.service.TokenProvider;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private TokenProvider tokenProvider;

    @MockBean
    private UserProfilePort userProfilePort;

    @MockBean
    private NotificationSender notificationSender;

    @MockBean
    private FederatedIdentityService federatedIdentityService;

    @Test
    @DisplayName("E2E Test: Full Authentication Lifecycle (Register -> Duplicate Check -> Verify OTP -> Login -> Lockout -> Refresh -> Logout)")
    void testFullAuthenticationLifecycle() throws Exception {
        String testEmail = "test_" + UUID.randomUUID() + "@eventplatform.vn";
        String testUsername = "user_" + UUID.randomUUID().toString().substring(0, 8);
        String testPassword = "Password123@";

        // 1. Đăng ký tài khoản thành công (kèm chuẩn hóa SĐT +84 -> 0)
        RegisterRequest registerReq = RegisterRequest.builder()
                .email(testEmail)
                .username(testUsername)
                .password(testPassword)
                .fullName("Nguyễn Test E2E")
                .phoneNumber("+84912345678")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.email").value(testEmail));

        // Kiểm tra trong PostgreSQL database
        Account account = accountRepository.findByEmail(testEmail).orElseThrow();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(account.getPhoneNumber()).isEqualTo("0912345678");

        // 2. Unhappy: Đăng ký trùng Email
        RegisterRequest duplicateEmailReq = RegisterRequest.builder()
                .email(testEmail)
                .username("another_user_" + UUID.randomUUID().toString().substring(0, 8))
                .password(testPassword)
                .fullName("Trùng Email")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateEmailReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_ALREADY_EXISTS.getCode()));

        // 3. Unhappy: Đăng ký trùng Username
        RegisterRequest duplicateUsernameReq = RegisterRequest.builder()
                .email("another_" + UUID.randomUUID() + "@gmail.com")
                .username(testUsername)
                .password(testPassword)
                .fullName("Trùng Username")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateUsernameReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_ALREADY_EXISTS.getCode()));

        // 4. Unhappy: Đăng nhập khi tài khoản chưa kích hoạt
        LoginRequest loginReq = LoginRequest.builder()
                .credential(testUsername)
                .password(testPassword)
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_PENDING_VERIFICATION.getCode()));

        // 5. Unhappy: Kích hoạt tài khoản với mã OTP sai
        VerifyEmailRequest invalidTokenReq = new VerifyEmailRequest();
        invalidTokenReq.setToken("999999");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidTokenReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_INVALID.getCode()));

        // 6. Lấy mã OTP thật từ database và kích hoạt thành công
        VerificationToken token = verificationTokenRepository
                .findTopByAccountAndTokenTypeAndIsUsedFalseOrderByCreatedAtDesc(account, VerificationTokenType.EMAIL_VERIFICATION)
                .orElseThrow();

        VerifyEmailRequest validTokenReq = new VerifyEmailRequest();
        validTokenReq.setToken(token.getTokenValue());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTokenReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        Account verifiedAccount = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(verifiedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        // 7. Unhappy: Kích hoạt lại bằng mã OTP đã dùng
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTokenReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_INVALID.getCode()));

        // 8. Unhappy: Đăng nhập sai mật khẩu -> tăng failedLoginAttempts lên 1
        LoginRequest wrongPassReq = LoginRequest.builder()
                .credential(testUsername)
                .password("WrongPassword123@")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPassReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.getCode()));

        Account afterWrongPass = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(afterWrongPass.getFailedLoginAttempts()).isEqualTo(1);

        // 9. Đăng nhập đúng thông tin -> Reset failedLoginAttempts về 0, nhận Access Token & Refresh Cookie
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();

        Account afterSuccessLogin = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(afterSuccessLogin.getFailedLoginAttempts()).isZero();

        String rawCookieHeader = loginResult.getResponse().getHeader("Set-Cookie");
        assertThat(rawCookieHeader).contains("refreshToken=");
        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();

        // 10. Sử dụng Refresh Token để cấp mới Access Token
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        Cookie rotatedRefreshCookie = refreshResult.getResponse().getCookie("refreshToken");
        assertThat(rotatedRefreshCookie).isNotNull();

        // 11. Unhappy: Tấn công Replay - dùng lại Refresh Token cũ đã bị xoay vòng (revoked) -> Thu hồi luôn cả rotatedRefreshCookie!
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_EXPIRED.getCode()));

        // Kiểm chứng Reuse Detection: rotatedRefreshCookie cũng đã bị thu hồi tự động để bảo vệ tài khoản
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(rotatedRefreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_EXPIRED.getCode()));

        // 12. Đăng xuất
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(rotatedRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("Unhappy & Anti-Sabotage Case: DTO Validation Errors on Registration (Username '@', Weak/Long Password, Invalid Phone)")
    void testRegistrationValidationErrors() throws Exception {
        // 1. Sai định dạng Email
        RegisterRequest invalidEmailReq = RegisterRequest.builder()
                .email("invalid-email-format")
                .username("validuser123")
                .password("Password123@")
                .fullName("Valid Name")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidEmailReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));

        // 2. Mật khẩu quá ngắn (< 8 ký tự)
        RegisterRequest weakPasswordReq = RegisterRequest.builder()
                .email("valid@example.com")
                .username("validuser123")
                .password("123")
                .fullName("Valid Name")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weakPasswordReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));

        // 3. Mật khẩu đủ 8 ký tự nhưng thiếu chữ hoa / ký tự đặc biệt
        RegisterRequest noSpecialPassReq = RegisterRequest.builder()
                .email("valid@example.com")
                .username("validuser123")
                .password("password12345")
                .fullName("Valid Name")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noSpecialPassReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));

        // 4. Mật khẩu quá 72 ký tự (Chặn tấn công BCrypt DoS)
        RegisterRequest giantPassReq = RegisterRequest.builder()
                .email("valid@example.com")
                .username("validuser123")
                .password("Aa1@" + "x".repeat(80))
                .fullName("Valid Name")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(giantPassReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));

        // 5. Username chứa ký tự '@' (Chặn tấn công DoS giả mạo email nạn nhân)
        RegisterRequest emailAsUsernameReq = RegisterRequest.builder()
                .email("attacker@example.com")
                .username("victim@gmail.com")
                .password("Password123@")
                .fullName("Attacker")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emailAsUsernameReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));

        // 6. Số điện thoại sai định dạng Việt Nam
        RegisterRequest badPhoneReq = RegisterRequest.builder()
                .email("valid@example.com")
                .username("validuser123")
                .password("Password123@")
                .fullName("Valid Name")
                .phoneNumber("0123456789")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPhoneReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));
    }

    @Test
    @DisplayName("E2E Test: Admin Security Policy Configuration + Brute-Force Auto Lockout + OTP Unlock")
    void testSecurityPolicyAndBruteForceLockoutAndOtpUnlock() throws Exception {
        UUID adminId = UUID.randomUUID();
        String adminToken = tokenProvider.generateAccessToken(adminId, "admin@event.vn", "admin", List.of("ROLE_ADMIN"));
        String userToken = tokenProvider.generateAccessToken(UUID.randomUUID(), "user@event.vn", "user", List.of("ROLE_ATTENDEE"));

        // 1. Unhappy: Khách ẩn danh hoặc User thường truy cập /api/v1/auth/security-policy -> 401 / 403
        mockMvc.perform(get("/api/v1/auth/security-policy"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/security-policy")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 2. Unhappy: Admin gửi thông số cấu hình phi lý (maxFailedAttempts = 0, lockoutDurationMinutes = -5) -> 400
        SecurityPolicyDto invalidPolicyReq = SecurityPolicyDto.builder()
                .maxFailedAttempts(0)
                .lockoutDurationMinutes(-5)
                .build();

        mockMvc.perform(put("/api/v1/auth/security-policy")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPolicyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));

        // 3. Happy: Admin cấu hình khóa sau 3 lần nhập sai trong 15 phút
        SecurityPolicyDto customPolicyReq = SecurityPolicyDto.builder()
                .maxFailedAttempts(3)
                .lockoutDurationMinutes(15)
                .otpExpirationMinutes(5)
                .maxOtpAttempts(3)
                .build();

        mockMvc.perform(put("/api/v1/auth/security-policy")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customPolicyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxFailedAttempts").value(3))
                .andExpect(jsonPath("$.data.lockoutDurationMinutes").value(15));

        try {
            // 4. Tạo tài khoản ACTIVE để test tự khóa sau 3 lần sai mật khẩu
            String email = "locktest_" + UUID.randomUUID() + "@eventplatform.vn";
            String username = "lock_" + UUID.randomUUID().toString().substring(0, 8);
            Account acc = accountRepository.save(Account.builder()
                    .email(email)
                    .username(username)
                    .passwordHash(passwordEncryptor.encode("CorrectPass123@"))
                    .status(AccountStatus.ACTIVE)
                    .build());

            LoginRequest wrongLogin = LoginRequest.builder()
                    .credential(email)
                    .password("WrongPass999@")
                    .build();

            // Lần 1 & 2 sai -> 400 INVALID_CREDENTIALS
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongLogin)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.getCode()));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongLogin)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.getCode()));

            // Lần 3 sai -> Chạm ngưỡng 3 lần -> Tự động khóa 403 ACCOUNT_LOCKED!
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongLogin)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_LOCKED.getCode()));

            Account lockedInDb = accountRepository.findById(acc.getId()).orElseThrow();
            assertThat(lockedInDb.getStatus()).isEqualTo(AccountStatus.LOCKED);
            assertThat(lockedInDb.getLockoutUntil()).isNotNull();

            // Nhập ĐÚNG mật khẩu lúc đang bị khóa vẫn bị chặn 403 ACCOUNT_LOCKED
            LoginRequest correctLogin = LoginRequest.builder()
                    .credential(email)
                    .password("CorrectPass123@")
                    .build();

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(correctLogin)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_LOCKED.getCode()));

            // 5. Yêu cầu OTP mở khóa tài khoản (/api/v1/auth/request-unlock)
            ForgotPasswordRequest unlockOtpReq = ForgotPasswordRequest.builder().email(email).build();
            mockMvc.perform(post("/api/v1/auth/request-unlock")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(unlockOtpReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"));

            // Spam yêu cầu OTP mở khóa lần 2 ngay lập tức (< 60s cooldown) -> 429 TOO_MANY_REQUESTS
            mockMvc.perform(post("/api/v1/auth/request-unlock")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(unlockOtpReq)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value(ErrorCode.TOO_MANY_REQUESTS.getCode()));

            // Nhập sai mã OTP mở khóa -> 400 TOKEN_INVALID
            UnlockAccountRequest wrongUnlockReq = UnlockAccountRequest.builder()
                    .email(email)
                    .otp("000000")
                    .build();
            mockMvc.perform(post("/api/v1/auth/unlock-account")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongUnlockReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_INVALID.getCode()));

            // Nhập đúng mã OTP mở khóa -> 200 OK -> Đăng nhập lại thành công
            VerificationToken unlockToken = verificationTokenRepository
                    .findTopByAccountAndTokenTypeAndIsUsedFalseOrderByCreatedAtDesc(lockedInDb, VerificationTokenType.ACCOUNT_UNLOCK)
                    .orElseThrow();

            UnlockAccountRequest validUnlockReq = UnlockAccountRequest.builder()
                    .email(email)
                    .otp(unlockToken.getTokenValue())
                    .build();
            mockMvc.perform(post("/api/v1/auth/unlock-account")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUnlockReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(correctLogin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        } finally {
            // Khôi phục chính sách mặc định (5 lần, 15 phút)
            SecurityPolicyDto defaultPolicy = SecurityPolicyDto.builder()
                    .maxFailedAttempts(5)
                    .lockoutDurationMinutes(15)
                    .otpExpirationMinutes(5)
                    .maxOtpAttempts(5)
                    .build();
            mockMvc.perform(put("/api/v1/auth/security-policy")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(defaultPolicy)));
        }
    }

    @Test
    @DisplayName("E2E Test: Forgot Password & Reset Password with OTP Brute-Force Protection")
    void testForgotPasswordAndResetPasswordAntiSabotage() throws Exception {
        String email = "reset_" + UUID.randomUUID() + "@eventplatform.vn";
        String username = "reset_" + UUID.randomUUID().toString().substring(0, 8);
        Account acc = accountRepository.save(Account.builder()
                .email(email)
                .username(username)
                .passwordHash(passwordEncryptor.encode("OldPassword123@"))
                .status(AccountStatus.ACTIVE)
                .build());

        ForgotPasswordRequest forgotReq = ForgotPasswordRequest.builder().email(email).build();

        // 1. Yêu cầu mã OTP quên mật khẩu thành công
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        // 2. Spam gọi lại ngay trong 60 giây -> bị chặn 429 TOO_MANY_REQUESTS
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOO_MANY_REQUESTS.getCode()));

        VerificationToken otpToken = verificationTokenRepository
                .findTopByAccountAndTokenTypeAndIsUsedFalseOrderByCreatedAtDesc(acc, VerificationTokenType.PASSWORD_RESET)
                .orElseThrow();

        // 3. Thử đặt lại mật khẩu trùng với mật khẩu cũ -> bị chặn 400 BAD_REQUEST
        ResetPasswordRequest samePassReq = ResetPasswordRequest.builder()
                .email(email)
                .otp(otpToken.getTokenValue())
                .newPassword("OldPassword123@")
                .build();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samePassReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));

        // 4. Đặt lại mật khẩu mới hợp lệ -> 200 OK
        ResetPasswordRequest validResetReq = ResetPasswordRequest.builder()
                .email(email)
                .otp(otpToken.getTokenValue())
                .newPassword("NewStrongPass456#")
                .build();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validResetReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        // 5. Dùng lại mã OTP vừa sử dụng -> bị chặn 400 TOKEN_INVALID
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validResetReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_INVALID.getCode()));

        // 6. Đăng nhập bằng mật khẩu mới thành công
        LoginRequest newLogin = LoginRequest.builder()
                .credential(email)
                .password("NewStrongPass456#")
                .build();
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("E2E Test: Logout-All Revokes All Sessions and Blacklists Access Tokens")
    void testLogoutAllAndBlacklist() throws Exception {
        String email = "logoutall_" + UUID.randomUUID() + "@eventplatform.vn";
        String username = "lout_" + UUID.randomUUID().toString().substring(0, 8);
        accountRepository.save(Account.builder()
                .email(email)
                .username(username)
                .passwordHash(passwordEncryptor.encode("Password123@"))
                .status(AccountStatus.ACTIVE)
                .build());

        LoginRequest loginReq = LoginRequest.builder()
                .credential(email)
                .password("Password123@")
                .build();

        // Đăng nhập trên Thiết bị 1 & Thiết bị 2
        MvcResult device1 = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json1 = objectMapper.readTree(device1.getResponse().getContentAsString());
        String accessToken1 = json1.path("data").path("accessToken").asText();
        Cookie refreshCookie1 = device1.getResponse().getCookie("refreshToken");

        MvcResult device2 = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie refreshCookie2 = device2.getResponse().getCookie("refreshToken");

        // Thiết bị 1 gọi /api/v1/auth/logout-all
        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + accessToken1)
                        .cookie(refreshCookie1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        // Dùng lại accessToken1 đã bị blacklist -> 401 UNAUTHORIZED
        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + accessToken1))
                .andExpect(status().isUnauthorized());

        // Cả refreshCookie1 lẫn refreshCookie2 của Thiết bị 2 đều đã bị thu hồi -> 401 UNAUTHORIZED
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(refreshCookie1))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(refreshCookie2))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unhappy Case: Login with Non-Existent or Locked Account")
    void testLoginWithInvalidAccountState() throws Exception {
        // 1. Đăng nhập với tài khoản hoàn toàn không tồn tại
        LoginRequest nonExistentReq = LoginRequest.builder()
                .credential("non_existent_user_99999")
                .password("Password123@")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonExistentReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.getCode()));

        // 2. Đăng nhập với tài khoản bị khóa (LOCKED)
        String lockedEmail = "locked_" + UUID.randomUUID() + "@eventplatform.vn";
        Account lockedAcc = Account.builder()
                .email(lockedEmail)
                .username("locked_" + UUID.randomUUID().toString().substring(0, 8))
                .passwordHash(passwordEncryptor.encode("Password123@"))
                .status(AccountStatus.LOCKED)
                .build();
        accountRepository.save(lockedAcc);

        LoginRequest lockedLoginReq = LoginRequest.builder()
                .credential(lockedEmail)
                .password("Password123@")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lockedLoginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_LOCKED.getCode()));
    }

    @Test
    @DisplayName("Unhappy Case: Resend Verification for Invalid Scenarios")
    void testResendVerificationUnhappyCases() throws Exception {
        // 1. Gửi lại OTP cho email không tồn tại -> 404 USER_NOT_FOUND
        ResendVerificationRequest notFoundReq = new ResendVerificationRequest();
        notFoundReq.setEmail("non_existent_" + UUID.randomUUID() + "@gmail.com");

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(notFoundReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));

        // 2. Gửi lại OTP cho tài khoản đã ACTIVE từ trước -> 400 BAD_REQUEST
        String activeEmail = "active_" + UUID.randomUUID() + "@eventplatform.vn";
        Account activeAcc = Account.builder()
                .email(activeEmail)
                .username("active_" + UUID.randomUUID().toString().substring(0, 8))
                .passwordHash(passwordEncryptor.encode("Password123@"))
                .status(AccountStatus.ACTIVE)
                .build();
        accountRepository.save(activeAcc);

        ResendVerificationRequest activeReq = new ResendVerificationRequest();
        activeReq.setEmail(activeEmail);

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activeReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));
    }

    @Test
    @DisplayName("Unhappy Case: Refresh Token with Missing or Tampered Cookie")
    void testRefreshTokenInvalidCases() throws Exception {
        // 1. Không gửi Cookie refreshToken
        mockMvc.perform(post("/api/v1/auth/refresh-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        // 2. Gửi Cookie refreshToken giả mạo / không tồn tại
        Cookie fakeCookie = new Cookie("refreshToken", "fake_token_value_random_12345");
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(fakeCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("E2E Test: Google OAuth2 Login (Happy & Unhappy)")
    void testGoogleLoginHappyAndUnhappy() throws Exception {
        // 1. Unhappy: Gửi Google token sai / không hợp lệ
        when(federatedIdentityService.verifyGoogleIdToken("invalid_google_token"))
                .thenThrow(new AppException(ErrorCode.TOKEN_INVALID, "Google ID Token không hợp lệ"));

        GoogleOAuthRequest invalidGoogleReq = new GoogleOAuthRequest("invalid_google_token");

        mockMvc.perform(post("/api/v1/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidGoogleReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_INVALID.getCode()));

        // 2. Happy: Gửi Google token hợp lệ -> Trả về Access Token & Cookie Refresh Token
        String googleEmail = "gg_" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        when(federatedIdentityService.verifyGoogleIdToken("valid_google_token"))
                .thenReturn(new FederatedIdentityService.GooglePrincipal(googleEmail, "Google Test User", "https://avatar.url/avatar.png"));

        GoogleOAuthRequest validGoogleReq = new GoogleOAuthRequest("valid_google_token");

        mockMvc.perform(post("/api/v1/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validGoogleReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(googleEmail));
    }
}
