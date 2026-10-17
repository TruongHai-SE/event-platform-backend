package vn.eventplatform.auth.service;

import io.jsonwebtoken.Claims;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.auth.client.CreateUserProfileCommand;
import vn.eventplatform.auth.client.UserProfileDto;
import vn.eventplatform.auth.client.UserProfilePort;
import vn.eventplatform.auth.domain.*;
import vn.eventplatform.auth.dto.*;
import vn.eventplatform.auth.repository.AccountRepository;
import vn.eventplatform.auth.repository.RefreshTokenRepository;
import vn.eventplatform.auth.repository.SecurityPolicyRepository;
import vn.eventplatform.auth.repository.VerificationTokenRepository;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final short DEFAULT_POLICY_ID = 1;
    private static final long OTP_COOLDOWN_SECONDS = 60L;

    private final AccountRepository accountRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityPolicyRepository securityPolicyRepository;
    private final PasswordEncryptor passwordEncryptor;
    private final TokenProvider tokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final NotificationSender notificationSender;
    private final FederatedIdentityService federatedIdentityService;
    private final UserProfilePort userProfilePort;

    @Value("${security.internal.secret}")
    private String internalSecret;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String normalizedUsername = request.getUsername().trim();
        String normalizedPhone = normalizePhone(request.getPhoneNumber());

        if (accountRepository.existsByEmail(normalizedEmail)) {
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_EXISTS, "Email đã được sử dụng");
        }
        if (accountRepository.existsByUsername(normalizedUsername)) {
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_EXISTS, "Tên đăng nhập đã được sử dụng");
        }
        if (normalizedPhone != null && accountRepository.existsByPhoneNumber(normalizedPhone)) {
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_EXISTS, "Số điện thoại đã được sử dụng");
        }

        Account account = Account.builder()
                .email(normalizedEmail)
                .username(normalizedUsername)
                .phoneNumber(normalizedPhone)
                .passwordHash(passwordEncryptor.encode(request.getPassword()))
                .status(AccountStatus.PENDING_VERIFICATION)
                .build();
        account = accountRepository.save(account);

        // 1. Tạo hồ sơ người dùng bên user-service qua OpenFeign
        try {
            CreateUserProfileCommand command = CreateUserProfileCommand.builder()
                    .id(account.getId())
                    .email(account.getEmail())
                    .username(account.getUsername())
                    .fullName(request.getFullName().trim())
                    .phoneNumber(normalizedPhone)
                    .build();
            userProfilePort.createProfile(internalSecret, command);
        } catch (Exception e) {
            log.error("Failed to create profile in user-service: {}", e.getMessage());
        }

        // 2. Sinh mã xác thực email (6 số an toàn bằng SecureRandom)
        String otpCode = generateSixDigitOtp();
        VerificationToken token = VerificationToken.builder()
                .account(account)
                .tokenValue(otpCode)
                .tokenType(VerificationTokenType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        verificationTokenRepository.save(token);

        // 3. Gửi email kích hoạt qua Brevo
        notificationSender.sendVerificationEmail(account.getEmail(), request.getFullName().trim(), otpCode);

        return RegisterResponse.builder()
                .accountId(account.getId())
                .email(account.getEmail())
                .username(account.getUsername())
                .message("Đăng ký thành công. Vui lòng kiểm tra email để kích hoạt tài khoản.")
                .build();
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        VerificationToken token = verificationTokenRepository
                .findByTokenValueAndTokenType(request.getToken().trim(), VerificationTokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new AppException(ErrorCode.TOKEN_INVALID, "Mã kích hoạt không đúng hoặc đã hết hạn"));

        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.TOKEN_INVALID, "Mã kích hoạt đã hết hạn hoặc đã được sử dụng");
        }

        Account account = token.getAccount();
        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);

        token.setUsed(true);
        verificationTokenRepository.save(token);
    }

    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        Account account = accountRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản với email này"));

        if (account.getStatus() == AccountStatus.ACTIVE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tài khoản đã được kích hoạt trước đó");
        }

        verificationTokenRepository.deleteByAccountAndTokenType(account, VerificationTokenType.EMAIL_VERIFICATION);

        String otpCode = generateSixDigitOtp();
        VerificationToken token = VerificationToken.builder()
                .account(account)
                .tokenValue(otpCode)
                .tokenType(VerificationTokenType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        verificationTokenRepository.save(token);

        notificationSender.sendVerificationEmail(account.getEmail(), account.getUsername(), otpCode);
    }

    @Transactional(noRollbackFor = AppException.class)
    public LoginResult login(LoginRequest request, String userAgent, String clientIp) {
        String rawCredential = request.getCredential().trim();
        String emailCandidate = rawCredential.toLowerCase(Locale.ROOT);

        Account account = accountRepository.findByEmailOrUsername(emailCandidate, rawCredential)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        // 1. Kiểm tra trạng thái khóa tài khoản trước khi kiểm tra mật khẩu
        if (account.getStatus() == AccountStatus.LOCKED) {
            if (account.getLockoutUntil() != null && !account.getLockoutUntil().isAfter(Instant.now())) {
                account.setStatus(AccountStatus.ACTIVE);
                account.setFailedLoginAttempts(0);
                account.setLockoutUntil(null);
                accountRepository.save(account);
            } else {
                throw new AppException(ErrorCode.ACCOUNT_LOCKED);
            }
        }

        // 2. Kiểm tra mật khẩu + đếm số lần sai động theo SecurityPolicy
        if (account.getPasswordHash() == null || !passwordEncryptor.matches(request.getPassword(), account.getPasswordHash())) {
            SecurityPolicy policy = getOrCreateSecurityPolicy();
            int attempts = account.getFailedLoginAttempts() + 1;
            account.setFailedLoginAttempts(attempts);

            if (attempts >= policy.getMaxFailedAttempts()) {
                account.setStatus(AccountStatus.LOCKED);
                account.setLockoutUntil(Instant.now().plus(policy.getLockoutDurationMinutes(), ChronoUnit.MINUTES));
                accountRepository.save(account);
                throw new AppException(
                        ErrorCode.ACCOUNT_LOCKED,
                        "Tài khoản đã bị khóa tạm thời do nhập sai mật khẩu quá " + policy.getMaxFailedAttempts() + " lần"
                );
            }

            accountRepository.save(account);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (account.getStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new AppException(ErrorCode.ACCOUNT_PENDING_VERIFICATION);
        }

        // 3. Đăng nhập đúng -> Reset bộ đếm sai
        if (account.getFailedLoginAttempts() > 0 || account.getLockoutUntil() != null) {
            account.setFailedLoginAttempts(0);
            account.setLockoutUntil(null);
            accountRepository.save(account);
        }

        List<String> roles = fetchUserRoles(account.getId());
        return issueTokens(account, roles, userAgent, clientIp);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        Account account = accountRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản với email này"));

        issuePurposeOtp(account, VerificationTokenType.PASSWORD_RESET);
    }

    @Transactional(noRollbackFor = AppException.class)
    public void resetPassword(ResetPasswordRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        Account account = accountRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản với email này"));

        VerificationToken token = verifyAndConsumeOtp(account, request.getOtp().trim(), VerificationTokenType.PASSWORD_RESET);

        if (account.getPasswordHash() != null && passwordEncryptor.matches(request.getNewPassword(), account.getPasswordHash())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mật khẩu mới không được trùng với mật khẩu hiện tại");
        }

        token.setUsed(true);
        verificationTokenRepository.save(token);

        account.setPasswordHash(passwordEncryptor.encode(request.getNewPassword()));
        account.setFailedLoginAttempts(0);
        account.setLockoutUntil(null);
        if (account.getStatus() == AccountStatus.LOCKED) {
            account.setStatus(AccountStatus.ACTIVE);
        }
        accountRepository.save(account);

        // Thu hồi toàn bộ phiên đăng nhập cũ sau khi đổi mật khẩu
        refreshTokenRepository.revokeAllByAccountId(account.getId());
        tokenBlacklistService.revokeAllUserTokens(account.getId(), tokenProvider.getAccessExpirationMs());
    }

    @Transactional
    public void requestUnlockOtp(ForgotPasswordRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        Account account = accountRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản với email này"));

        if (account.getStatus() != AccountStatus.LOCKED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tài khoản không ở trạng thái bị khóa");
        }

        issuePurposeOtp(account, VerificationTokenType.ACCOUNT_UNLOCK);
    }

    @Transactional(noRollbackFor = AppException.class)
    public void unlockAccount(UnlockAccountRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        Account account = accountRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản với email này"));

        if (account.getStatus() != AccountStatus.LOCKED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tài khoản không ở trạng thái bị khóa");
        }

        // Cho phép mở khóa bằng OTP ACCOUNT_UNLOCK hoặc PASSWORD_RESET còn hiệu lực
        VerificationTokenType typeToVerify = verificationTokenRepository
                .findTopByAccountAndTokenTypeAndIsUsedFalseOrderByCreatedAtDesc(account, VerificationTokenType.ACCOUNT_UNLOCK)
                .isPresent()
                ? VerificationTokenType.ACCOUNT_UNLOCK
                : VerificationTokenType.PASSWORD_RESET;

        VerificationToken token = verifyAndConsumeOtp(account, request.getOtp().trim(), typeToVerify);
        token.setUsed(true);
        verificationTokenRepository.save(token);

        account.setStatus(AccountStatus.ACTIVE);
        account.setFailedLoginAttempts(0);
        account.setLockoutUntil(null);
        accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public SecurityPolicyDto getSecurityPolicy() {
        SecurityPolicy policy = getOrCreateSecurityPolicy();
        return mapPolicyToDto(policy);
    }

    @Transactional
    public SecurityPolicyDto updateSecurityPolicy(SecurityPolicyDto request, UUID adminId) {
        SecurityPolicy policy = getOrCreateSecurityPolicy();
        policy.setMaxFailedAttempts(request.getMaxFailedAttempts());
        policy.setLockoutDurationMinutes(request.getLockoutDurationMinutes());
        if (request.getOtpExpirationMinutes() != null) {
            policy.setOtpExpirationMinutes(request.getOtpExpirationMinutes());
        }
        if (request.getMaxOtpAttempts() != null) {
            policy.setMaxOtpAttempts(request.getMaxOtpAttempts());
        }
        policy.setUpdatedBy(adminId);
        policy.setUpdatedAt(Instant.now());
        policy = securityPolicyRepository.save(policy);
        return mapPolicyToDto(policy);
    }

    @Transactional
    public LoginResult loginWithGoogle(GoogleOAuthRequest request, String userAgent, String clientIp) {
        FederatedIdentityService.GooglePrincipal principal = federatedIdentityService.verifyGoogleIdToken(request.getIdToken());
        String normalizedEmail = principal.email().trim().toLowerCase(Locale.ROOT);

        Account account = accountRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> {
                    String baseUsername = normalizedEmail.split("@")[0].replaceAll("[^a-zA-Z0-9._-]", "_");
                    if (baseUsername.length() > 40) {
                        baseUsername = baseUsername.substring(0, 40);
                    }
                    String generatedUsername = baseUsername + "_" + UUID.randomUUID().toString().substring(0, 5);

                    Account newAcc = Account.builder()
                            .email(normalizedEmail)
                            .username(generatedUsername)
                            .status(AccountStatus.ACTIVE)
                            .build();
                    newAcc = accountRepository.save(newAcc);

                    try {
                        CreateUserProfileCommand cmd = CreateUserProfileCommand.builder()
                                .id(newAcc.getId())
                                .email(newAcc.getEmail())
                                .username(newAcc.getUsername())
                                .fullName(principal.name() != null ? principal.name() : newAcc.getUsername())
                                .avatarUrl(principal.pictureUrl())
                                .build();
                        userProfilePort.createProfile(internalSecret, cmd);
                    } catch (Exception e) {
                        log.error("Failed to create Google user profile: {}", e.getMessage());
                    }
                    return newAcc;
                });

        if (account.getStatus() == AccountStatus.LOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        List<String> roles = fetchUserRoles(account.getId());
        return issueTokens(account, roles, userAgent, clientIp);
    }

    @Transactional(noRollbackFor = AppException.class)
    public LoginResult refreshToken(String rawRefreshToken, String userAgent, String clientIp) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Refresh Token không được tìm thấy");
        }

        String tokenHash = tokenProvider.hashToken(rawRefreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Refresh Token không hợp lệ"));

        // Phát hiện tấn công Replay (Reuse Detection): Nếu token đã bị thu hồi mà vẫn mang ra dùng lại -> Thu hồi toàn bộ phiên của user!
        if (storedToken.isRevoked()) {
            UUID accountId = storedToken.getAccount().getId();
            log.warn("SECURITY ALERT: Refresh token reuse detected for account {}! Revoking all active sessions.", accountId);
            refreshTokenRepository.revokeAllByAccountId(accountId);
            tokenBlacklistService.revokeAllUserTokens(accountId, tokenProvider.getAccessExpirationMs());
            throw new AppException(ErrorCode.TOKEN_EXPIRED, "Refresh Token đã hết hạn hoặc bị thu hồi");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new AppException(ErrorCode.TOKEN_EXPIRED, "Refresh Token đã hết hạn hoặc bị thu hồi");
        }

        // Xoay vòng Token: Thu hồi token cũ
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        Account account = storedToken.getAccount();
        if (account.getStatus() == AccountStatus.LOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        List<String> roles = fetchUserRoles(account.getId());
        return issueTokens(account, roles, userAgent, clientIp);
    }

    @Transactional
    public void logout(String rawRefreshToken, String authorizationHeader) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = tokenProvider.hashToken(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
        blacklistBearerTokenIfPresent(authorizationHeader);
    }

    @Transactional
    public void logoutAll(UUID accountId, String rawRefreshToken, String authorizationHeader) {
        refreshTokenRepository.revokeAllByAccountId(accountId);
        tokenBlacklistService.revokeAllUserTokens(accountId, tokenProvider.getAccessExpirationMs());
        logout(rawRefreshToken, authorizationHeader);
    }

    private void blacklistBearerTokenIfPresent(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String rawAccessToken = authorizationHeader.substring(7).trim();
            try {
                Claims claims = tokenProvider.parseToken(rawAccessToken);
                if (claims.getId() != null && claims.getExpiration() != null) {
                    tokenBlacklistService.blacklistJti(claims.getId(), claims.getExpiration().toInstant());
                }
            } catch (Exception e) {
                log.debug("Ignored invalid bearer token on logout: {}", e.getMessage());
            }
        }
    }

    private void issuePurposeOtp(Account account, VerificationTokenType tokenType) {
        verificationTokenRepository.findTopByAccountAndTokenTypeAndIsUsedFalseOrderByCreatedAtDesc(account, tokenType)
                .ifPresent(existing -> {
                    if (!existing.getExpiresAt().isBefore(Instant.now())
                            && existing.getCreatedAt().isAfter(Instant.now().minus(OTP_COOLDOWN_SECONDS, ChronoUnit.SECONDS))) {
                        throw new AppException(
                                ErrorCode.TOO_MANY_REQUESTS,
                                "Vui lòng đợi 60 giây trước khi yêu cầu mã OTP mới"
                        );
                    }
                });

        verificationTokenRepository.markAllUsedByAccountAndTokenType(account, tokenType);

        SecurityPolicy policy = getOrCreateSecurityPolicy();
        String otpCode = generateSixDigitOtp();

        VerificationToken token = VerificationToken.builder()
                .account(account)
                .tokenValue(otpCode)
                .tokenType(tokenType)
                .attemptCount(0)
                .expiresAt(Instant.now().plus(policy.getOtpExpirationMinutes(), ChronoUnit.MINUTES))
                .build();
        verificationTokenRepository.save(token);

        if (tokenType == VerificationTokenType.PASSWORD_RESET) {
            notificationSender.sendPasswordResetEmail(account.getEmail(), account.getUsername(), otpCode);
        } else {
            notificationSender.sendUnlockAccountEmail(account.getEmail(), account.getUsername(), otpCode);
        }
    }

    private VerificationToken verifyAndConsumeOtp(Account account, String inputOtp, VerificationTokenType tokenType) {
        VerificationToken token = verificationTokenRepository
                .findTopByAccountAndTokenTypeAndIsUsedFalseOrderByCreatedAtDesc(account, tokenType)
                .orElseThrow(() -> new AppException(ErrorCode.TOKEN_INVALID, "Mã OTP không tồn tại hoặc đã được sử dụng"));

        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            token.setUsed(true);
            verificationTokenRepository.save(token);
            throw new AppException(ErrorCode.TOKEN_INVALID, "Mã OTP đã hết hạn");
        }

        SecurityPolicy policy = getOrCreateSecurityPolicy();
        if (token.getAttemptCount() >= policy.getMaxOtpAttempts()) {
            token.setUsed(true);
            verificationTokenRepository.save(token);
            throw new AppException(ErrorCode.TOKEN_INVALID, "Đã vượt quá số lần nhập sai mã OTP cho phép");
        }

        boolean matches = MessageDigest.isEqual(
                token.getTokenValue().getBytes(StandardCharsets.UTF_8),
                inputOtp.getBytes(StandardCharsets.UTF_8)
        );
        if (!matches) {
            int nextAttempt = token.getAttemptCount() + 1;
            token.setAttemptCount(nextAttempt);
            if (nextAttempt >= policy.getMaxOtpAttempts()) {
                token.setUsed(true);
            }
            verificationTokenRepository.save(token);
            throw new AppException(ErrorCode.TOKEN_INVALID, "Mã OTP không chính xác");
        }

        return token;
    }

    private SecurityPolicy getOrCreateSecurityPolicy() {
        return securityPolicyRepository.findById(DEFAULT_POLICY_ID)
                .orElseGet(() -> securityPolicyRepository.save(SecurityPolicy.builder()
                        .id(DEFAULT_POLICY_ID)
                        .maxFailedAttempts(5)
                        .lockoutDurationMinutes(15)
                        .otpExpirationMinutes(5)
                        .maxOtpAttempts(5)
                        .updatedAt(Instant.now())
                        .build()));
    }

    private SecurityPolicyDto mapPolicyToDto(SecurityPolicy policy) {
        return SecurityPolicyDto.builder()
                .maxFailedAttempts(policy.getMaxFailedAttempts())
                .lockoutDurationMinutes(policy.getLockoutDurationMinutes())
                .otpExpirationMinutes(policy.getOtpExpirationMinutes())
                .maxOtpAttempts(policy.getMaxOtpAttempts())
                .updatedBy(policy.getUpdatedBy())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }

    private static String generateSixDigitOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(900_000) + 100_000);
    }

    public static String normalizePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        String cleaned = rawPhone.trim();
        if (cleaned.startsWith("+84")) {
            return "0" + cleaned.substring(3);
        }
        if (cleaned.startsWith("84") && cleaned.length() == 11) {
            return "0" + cleaned.substring(2);
        }
        return cleaned;
    }

    private LoginResult issueTokens(Account account, List<String> roles, String userAgent, String clientIp) {
        String accessToken = tokenProvider.generateAccessToken(
                account.getId(),
                account.getEmail(),
                account.getUsername(),
                roles
        );

        String rawRefreshToken = tokenProvider.generateRawRefreshToken();
        String refreshHash = tokenProvider.hashToken(rawRefreshToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .account(account)
                .tokenHash(refreshHash)
                .userAgent(userAgent)
                .clientIp(clientIp)
                .expiresAt(tokenProvider.calculateRefreshExpiry())
                .build();
        refreshTokenRepository.save(refreshToken);

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessExpirationMs() / 1000)
                .user(TokenResponse.UserSummary.builder()
                        .id(account.getId())
                        .email(account.getEmail())
                        .username(account.getUsername())
                        .fullName(account.getUsername())
                        .roles(roles)
                        .build())
                .build();

        return LoginResult.builder()
                .tokenResponse(tokenResponse)
                .rawRefreshToken(rawRefreshToken)
                .build();
    }

    private List<String> fetchUserRoles(UUID accountId) {
        try {
            ApiResponse<UserProfileDto> resp = userProfilePort.getProfileById(internalSecret, accountId);
            if (resp != null && resp.getData() != null && resp.getData().getRoles() != null) {
                return resp.getData().getRoles();
            }
        } catch (Exception e) {
            log.warn("Could not fetch roles from user-service for account {}: {}", accountId, e.getMessage());
        }
        return Collections.singletonList("ROLE_ATTENDEE");
    }

    @Getter
    @Builder
    public static class LoginResult {
        private TokenResponse tokenResponse;
        private String rawRefreshToken;
    }
}
