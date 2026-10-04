package vn.eventplatform.auth;

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
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

        // 1. Đăng ký tài khoản thành công
        RegisterRequest registerReq = RegisterRequest.builder()
                .email(testEmail)
                .username(testUsername)
                .password(testPassword)
                .fullName("Nguyễn Test E2E")
                .phoneNumber("0912345678")
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
                .findAll().stream()
                .filter(t -> t.getAccount().getId().equals(account.getId()))
                .findFirst()
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

        // 8. Unhappy: Đăng nhập sai mật khẩu
        LoginRequest wrongPassReq = LoginRequest.builder()
                .credential(testUsername)
                .password("WrongPassword123@")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPassReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.getCode()));

        // 9. Đăng nhập đúng thông tin -> Nhận Access Token & Refresh Cookie
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();

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

        // 11. Unhappy: Tấn công Replay - dùng lại Refresh Token cũ đã bị xoay vòng (revoked)
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_EXPIRED.getCode()));

        // 12. Đăng xuất
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(rotatedRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("Unhappy Case: DTO Validation Errors on Registration")
    void testRegistrationValidationErrors() throws Exception {
        // Sai định dạng Email
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

        // Mật khẩu quá ngắn (< 8 ký tự)
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

        // Username để trống
        RegisterRequest blankUsernameReq = RegisterRequest.builder()
                .email("valid@example.com")
                .username("")
                .password("Password123@")
                .fullName("Valid Name")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankUsernameReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getCode()));
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
