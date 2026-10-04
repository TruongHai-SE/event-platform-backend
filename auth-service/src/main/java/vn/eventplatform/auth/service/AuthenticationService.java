package vn.eventplatform.auth.service;

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
import vn.eventplatform.auth.repository.VerificationTokenRepository;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AccountRepository accountRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncryptor passwordEncryptor;
    private final TokenProvider tokenProvider;
    private final NotificationSender notificationSender;
    private final FederatedIdentityService federatedIdentityService;
    private final UserProfilePort userProfilePort;

    @Value("${security.internal.secret}")
    private String internalSecret;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_EXISTS, "Email đã được sử dụng");
        }
        if (accountRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_EXISTS, "Tên đăng nhập đã được sử dụng");
        }

        Account account = Account.builder()
                .email(request.getEmail())
                .username(request.getUsername())
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
                    .fullName(request.getFullName())
                    .phoneNumber(request.getPhoneNumber())
                    .build();
            userProfilePort.createProfile(internalSecret, command);
        } catch (Exception e) {
            log.error("Failed to create profile in user-service: {}", e.getMessage());
            // Feign fail is non-fatal if user-service is starting up, but logged
        }

        // 2. Sinh mã xác thực email (6 số)
        String otpCode = String.format("%06d", (int) (Math.random() * 900000) + 100000);
        VerificationToken token = VerificationToken.builder()
                .account(account)
                .tokenValue(otpCode)
                .tokenType(VerificationTokenType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        verificationTokenRepository.save(token);

        // 3. Gửi email kích hoạt thật qua Brevo
        notificationSender.sendVerificationEmail(account.getEmail(), request.getFullName(), otpCode);

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
                .findByTokenValueAndTokenType(request.getToken(), VerificationTokenType.EMAIL_VERIFICATION)
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
        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản với email này"));

        if (account.getStatus() == AccountStatus.ACTIVE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tài khoản đã được kích hoạt trước đó");
        }

        verificationTokenRepository.deleteByAccountAndTokenType(account, VerificationTokenType.EMAIL_VERIFICATION);

        String otpCode = String.format("%06d", (int) (Math.random() * 900000) + 100000);
        VerificationToken token = VerificationToken.builder()
                .account(account)
                .tokenValue(otpCode)
                .tokenType(VerificationTokenType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        verificationTokenRepository.save(token);

        notificationSender.sendVerificationEmail(account.getEmail(), account.getUsername(), otpCode);
    }

    @Transactional
    public LoginResult login(LoginRequest request, String userAgent, String clientIp) {
        Account account = accountRepository.findByEmailOrUsername(request.getCredential(), request.getCredential())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (account.getPasswordHash() == null || !passwordEncryptor.matches(request.getPassword(), account.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (account.getStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new AppException(ErrorCode.ACCOUNT_PENDING_VERIFICATION);
        }
        if (account.getStatus() == AccountStatus.LOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        // Lấy thông tin vai trò từ user-service
        List<String> roles = fetchUserRoles(account.getId());

        return issueTokens(account, roles, userAgent, clientIp);
    }

    @Transactional
    public LoginResult loginWithGoogle(GoogleOAuthRequest request, String userAgent, String clientIp) {
        FederatedIdentityService.GooglePrincipal principal = federatedIdentityService.verifyGoogleIdToken(request.getIdToken());

        Account account = accountRepository.findByEmail(principal.email())
                .orElseGet(() -> {
                    String baseUsername = principal.email().split("@")[0];
                    if (baseUsername.length() > 40) {
                        baseUsername = baseUsername.substring(0, 40);
                    }
                    String generatedUsername = baseUsername + "_" + UUID.randomUUID().toString().substring(0, 5);

                    Account newAcc = Account.builder()
                            .email(principal.email())
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

    @Transactional
    public LoginResult refreshToken(String rawRefreshToken, String userAgent, String clientIp) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Refresh Token không được tìm thấy");
        }

        String tokenHash = tokenProvider.hashToken(rawRefreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Refresh Token không hợp lệ"));

        if (storedToken.isRevoked() || storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED, "Refresh Token đã hết hạn hoặc bị thu hồi");
        }

        // Xoay vòng Token: Thu hồi token cũ
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        Account account = storedToken.getAccount();
        List<String> roles = fetchUserRoles(account.getId());

        return issueTokens(account, roles, userAgent, clientIp);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = tokenProvider.hashToken(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
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
                .expiresIn(900)
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
