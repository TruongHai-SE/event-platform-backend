package vn.eventplatform.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import vn.eventplatform.gateway.config.GatewaySecurityAndRoutingConfig;
import vn.eventplatform.gateway.config.GatewaySecurityAndRoutingConfig.GatewaySecurityStateService;

import javax.crypto.SecretKey;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@Import(GatewayFlowIntegrationTest.TestDownstreamEchoController.class)
class GatewayFlowIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private GatewaySecurityStateService securityStateService;

    @Value("${security.token.secret-key}")
    private String secret;

    private WebTestClient webTestClient;
    private SecretKey secretKey;

    @RestController
    static class TestDownstreamEchoController {
        @RequestMapping("/__downstream/echo")
        public Mono<Map<String, Object>> echo(ServerHttpRequest request) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("requestId", request.getHeaders().getFirst(GatewaySecurityAndRoutingConfig.HEADER_REQUEST_ID));
            data.put("userId", request.getHeaders().getFirst(GatewaySecurityAndRoutingConfig.HEADER_USER_ID));
            data.put("userEmail", request.getHeaders().getFirst(GatewaySecurityAndRoutingConfig.HEADER_USER_EMAIL));
            data.put("userRole", request.getHeaders().getFirst(GatewaySecurityAndRoutingConfig.HEADER_USER_ROLE));
            data.put("userRoles", request.getHeaders().getFirst(GatewaySecurityAndRoutingConfig.HEADER_USER_ROLES));
            data.put("roles", request.getHeaders().getFirst(GatewaySecurityAndRoutingConfig.HEADER_ROLES));
            data.put("internalSecret", request.getHeaders().getFirst(GatewaySecurityAndRoutingConfig.HEADER_INTERNAL_SECRET));
            data.put("internalCall", request.getHeaders().getFirst(GatewaySecurityAndRoutingConfig.HEADER_INTERNAL_CALL));
            data.put("forwardedPrefix", request.getHeaders().getFirst(GatewaySecurityAndRoutingConfig.HEADER_FORWARDED_PREFIX));
            return Mono.just(Map.of(
                    "success", true,
                    "code", "SUCCESS",
                    "data", data
            ));
        }

        @RequestMapping("/__downstream/crashed")
        public Mono<Map<String, Object>> crashed() {
            return Mono.error(new ConnectException("Connection refused: analytics-service:8090"));
        }
    }

    @BeforeEach
    void setUp() {
        securityStateService.clearTestState();
        this.webTestClient = WebTestClient.bindToApplicationContext(applicationContext)
                .configureClient()
                .baseUrl("http://localhost:8080")
                .build();
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private String generateToken(UUID userId, String email, List<String> roles, String jti, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .id(jti)
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    @Test
    @DisplayName("1. Anti-Sabotage: Chặn cứng mọi truy cập /internal/** (403) và Path Traversal / Dirty URI (.., //, ;, %2e, %2f) (400)")
    void testBlockInternalRoutesAndPathTraversal() {
        // 1.1 Gọi trực tiếp /internal/v1/vouchers/reserve qua Gateway -> 403 FORBIDDEN
        webTestClient.post().uri("/internal/v1/vouchers/reserve")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("ERR_FORBIDDEN");

        // 1.2 Gọi /internal/v1/ad-campaigns/{id}/settlement -> 403 FORBIDDEN
        webTestClient.post().uri("/internal/v1/ad-campaigns/" + UUID.randomUUID() + "/settlement")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ERR_FORBIDDEN");

        // 1.3 Gọi /internal/v1/users/profile -> 403 FORBIDDEN
        webTestClient.post().uri("/internal/v1/users/profile")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ERR_FORBIDDEN");

        // 1.4 Single-encoded (%2e%2e) & Double-encoded (%252e%252e) path traversal -> 400 BAD_REQUEST
        webTestClient.get().uri("/api/v1/events/public/%2e%2e/internal")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ERR_BAD_REQUEST");

        webTestClient.get().uri("/api/v1/events/public/%252e%252e/internal")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ERR_BAD_REQUEST");

        // 1.5 Semicolon matrix param bypass (;v=1) & encoded slash (%2f) -> 400 BAD_REQUEST
        webTestClient.get().uri("/api/v1/events/public;v=1")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ERR_BAD_REQUEST");

        webTestClient.get().uri("/api/v1/events/public%2fadmin")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ERR_BAD_REQUEST");
    }

    @Test
    @DisplayName("2. Anti-Sabotage: Chống giả mạo Header X-User-Id / X-User-Role / X-Roles / X-Internal-Secret và Optional Auth trên Public GET")
    void testPreventHeaderSpoofingAndOptionalAuth() {
        UUID realUserId = UUID.randomUUID();
        UUID spoofedAdminId = UUID.randomUUID();

        // Kịch bản A: Không có JWT nhưng gửi kèm X-User-Id, X-User-Role, X-Roles=ROLE_ADMIN vào endpoint bảo vệ -> 401 UNAUTHORIZED
        webTestClient.get().uri("/api/v1/users/me")
                .header("X-User-Id", spoofedAdminId.toString())
                .header("X-User-Role", "ROLE_ADMIN")
                .header("X-Roles", "ROLE_ADMIN")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ERR_UNAUTHORIZED");

        // Kịch bản B: Không có JWT nhưng gửi kèm X-User-Id, X-Roles=ROLE_ADMIN, X-Internal-Secret vào Public Endpoint -> Bị xóa sạch 100%
        webTestClient.get().uri("/api/v1/categories")
                .header("X-User-Id", spoofedAdminId.toString())
                .header("X-User-Role", "ROLE_ADMIN")
                .header("X-Roles", "ROLE_ADMIN")
                .header("X-Internal-Secret", "hacked")
                .header("X-Internal-Call", "true")
                .header("X-Forwarded-Prefix", "/admin")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.userId").isEmpty()
                .jsonPath("$.data.userRole").isEmpty()
                .jsonPath("$.data.roles").isEmpty()
                .jsonPath("$.data.internalSecret").isEmpty()
                .jsonPath("$.data.internalCall").isEmpty()
                .jsonPath("$.data.forwardedPrefix").isEmpty();

        // Kịch bản C: Có JWT của ROLE_USER nhưng cố tình gửi kèm Header X-User-Role=ROLE_ADMIN & X-Roles=ROLE_ADMIN -> Bị ghi đè thành ROLE_USER
        String userToken = generateToken(
                realUserId, "user@event.vn", List.of("ROLE_USER"),
                UUID.randomUUID().toString(), Instant.now(), Instant.now().plusSeconds(900));

        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .header("X-User-Id", spoofedAdminId.toString())
                .header("X-User-Role", "ROLE_ADMIN")
                .header("X-Roles", "ROLE_ADMIN")
                .header("X-Request-Id", "req-trace-001")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "req-trace-001")
                .expectBody()
                .jsonPath("$.data.requestId").isEqualTo("req-trace-001")
                .jsonPath("$.data.userId").isEqualTo(realUserId.toString())
                .jsonPath("$.data.userEmail").isEqualTo("user@event.vn")
                .jsonPath("$.data.userRole").isEqualTo("ROLE_USER")
                .jsonPath("$.data.roles").isEqualTo("ROLE_USER");

        // Kịch bản D: Optional Auth trên Public GET (/api/v1/categories) khi Admin gửi kèm Bearer Token hợp lệ -> Được gắn X-User-Id & X-Roles=ROLE_ADMIN
        UUID adminId = UUID.randomUUID();
        String adminToken = generateToken(
                adminId, "admin@event.vn", List.of("ROLE_ADMIN"),
                UUID.randomUUID().toString(), Instant.now(), Instant.now().plusSeconds(900));

        webTestClient.get().uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.userId").isEqualTo(adminId.toString())
                .jsonPath("$.data.userRole").isEqualTo("ROLE_ADMIN")
                .jsonPath("$.data.roles").isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("3. Method-Aware Public Whitelist: Mở đúng GET công khai, chặn chặt POST/PUT/DELETE và sub-path nhạy cảm")
    void testMethodAwarePublicWhitelist() {
        // GET /api/v1/categories & GET /api/v1/categories/music-festivals -> Public 200 OK
        webTestClient.get().uri("/api/v1/categories").exchange().expectStatus().isOk();
        webTestClient.get().uri("/api/v1/categories/music-festivals").exchange().expectStatus().isOk();

        // NHƯNG GET /api/v1/categories/requests (danh sách đề xuất nội bộ) -> Bắt buộc có JWT (401 UNAUTHORIZED)
        webTestClient.get().uri("/api/v1/categories/requests")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ERR_UNAUTHORIZED");

        // POST / PUT / DELETE /api/v1/categories không có token -> Bị chặn ngay tại Gateway (401 UNAUTHORIZED)
        webTestClient.post().uri("/api/v1/categories").exchange().expectStatus().isUnauthorized();
        webTestClient.put().uri("/api/v1/categories/" + UUID.randomUUID()).exchange().expectStatus().isUnauthorized();
        webTestClient.delete().uri("/api/v1/categories/" + UUID.randomUUID()).exchange().expectStatus().isUnauthorized();

        // GET /api/v1/events/public -> Public 200 OK, nhưng GET /api/v1/events (quản lý) -> 401 UNAUTHORIZED
        webTestClient.get().uri("/api/v1/events/public").exchange().expectStatus().isOk();
        webTestClient.get().uri("/api/v1/events").exchange().expectStatus().isUnauthorized();

        // GET /api/v1/public/banners & /api/v1/public/ad-position-prices & /uploads/banners/test.png -> Public 200 OK
        webTestClient.get().uri("/api/v1/public/banners").exchange().expectStatus().isOk();
        webTestClient.get().uri("/api/v1/public/ad-position-prices").exchange().expectStatus().isOk();
        webTestClient.get().uri("/uploads/banners/test.png").exchange().expectStatus().isOk();

        // POST /api/v1/auth/login -> Public, nhưng POST /api/v1/auth/logout-all & GET /api/v1/auth/security-policy -> 401 UNAUTHORIZED
        webTestClient.post().uri("/api/v1/auth/login").exchange().expectStatus().isOk();
        webTestClient.post().uri("/api/v1/auth/logout-all").exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/api/v1/auth/security-policy").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("4. JWT Verification, Redis Blacklist (jti & user revoked_at) và Admin Route Protection")
    void testJwtValidationBlacklistAndAdminGuard() {
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        Instant issuedAt = Instant.now().minusSeconds(60);

        // 4.1 Token hết hạn -> 401 ERR_TOKEN_EXPIRED
        String expiredToken = generateToken(
                userId, "u@event.vn", List.of("ROLE_USER"),
                UUID.randomUUID().toString(), Instant.now().minusSeconds(1200), Instant.now().minusSeconds(60));
        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ERR_TOKEN_EXPIRED");

        // 4.2 Token sai chữ ký -> 401 ERR_TOKEN_INVALID
        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.invalid_sig")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ERR_TOKEN_INVALID");

        // 4.3 Token hợp lệ nhưng bị thu hồi theo jti (Blacklist) -> 401 ERR_TOKEN_EXPIRED
        String validToken = generateToken(
                userId, "u@event.vn", List.of("ROLE_USER"),
                jti, issuedAt, Instant.now().plusSeconds(900));
        securityStateService.blacklistJtiInMemory(jti, Duration.ofMinutes(15));

        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ERR_TOKEN_EXPIRED");

        // 4.4 Thu hồi toàn bộ phiên của user (logout-all / reset-password) -> 401 ERR_TOKEN_EXPIRED
        UUID user2Id = UUID.randomUUID();
        String user2Token = generateToken(
                user2Id, "u2@event.vn", List.of("ROLE_USER"),
                UUID.randomUUID().toString(), issuedAt, Instant.now().plusSeconds(900));
        securityStateService.revokeAllUserTokensInMemory(user2Id, Instant.now().toEpochMilli());

        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user2Token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ERR_TOKEN_EXPIRED");

        // 4.5 User thường gọi vào đường dẫn Admin (/api/v1/admin/ad-packages, /api/v1/auth/security-policy, /api/v1/roles) -> 403 ERR_FORBIDDEN
        UUID activeUserId = UUID.randomUUID();
        String activeUserToken = generateToken(
                activeUserId, "active@event.vn", List.of("ROLE_USER"),
                UUID.randomUUID().toString(), Instant.now(), Instant.now().plusSeconds(900));

        webTestClient.get().uri("/api/v1/admin/ad-packages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + activeUserToken)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ERR_FORBIDDEN");

        webTestClient.get().uri("/api/v1/auth/security-policy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + activeUserToken)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ERR_FORBIDDEN");

        webTestClient.get().uri("/api/v1/roles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + activeUserToken)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ERR_FORBIDDEN");

        // Admin gọi vào /api/v1/admin/ad-packages -> 200 OK
        String adminToken = generateToken(
                UUID.randomUUID(), "admin@event.vn", List.of("ROLE_ADMIN"),
                UUID.randomUUID().toString(), Instant.now(), Instant.now().plusSeconds(900));
        webTestClient.get().uri("/api/v1/admin/ad-packages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.userRole").isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("5. Rate Limiting: Giới hạn tốc độ theo IP cho /api/v1/auth/** trả 429 TOO_MANY_REQUESTS kèm Retry-After")
    void testRateLimitingOnAuthEndpoint() {
        String attackerIp = "203.0.113.99";

        // Trong application-test.yml, auth-requests-per-minute = 5
        for (int i = 1; i <= 5; i++) {
            webTestClient.post().uri("/api/v1/auth/login")
                    .header("X-Forwarded-For", attackerIp)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("X-RateLimit-Limit", "5");
        }

        // Request thứ 6 từ cùng IP -> Bị chặn 429 TOO_MANY_REQUESTS kèm header Retry-After: 60
        webTestClient.post().uri("/api/v1/auth/login")
                .header("X-Forwarded-For", attackerIp)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "60")
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0")
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("ERR_TOO_MANY_REQUESTS");
    }

    @Test
    @DisplayName("6. Fallback, Downstream Crash (503), 404 Unified Error, CORS Preflight & Health Check")
    void testFallbackUnifiedErrorCorsAndHealthCheck() {
        // 6.1 Health Check endpoint (/actuator/health) -> Public 200 OK
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");

        // 6.2 Fallback endpoint (/fallback/order-service) -> 503 ERR_SERVICE_UNAVAILABLE
        webTestClient.get().uri("/fallback/order-service")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("ERR_SERVICE_UNAVAILABLE");

        // 6.3 Downstream service bị sập (ConnectException) -> Gateway bắt lỗi trả JSON 503 ERR_SERVICE_UNAVAILABLE
        String userToken = generateToken(
                UUID.randomUUID(), "user@event.vn", List.of("ROLE_USER"),
                UUID.randomUUID().toString(), Instant.now(), Instant.now().plusSeconds(900));
        webTestClient.get().uri("/api/v1/analytics/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("ERR_SERVICE_UNAVAILABLE");

        // 6.4 Gọi đường dẫn không tồn tại -> 404 ERR_NOT_FOUND dạng JSON chuẩn
        webTestClient.get().uri("/api/v1/non-existent-module/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("ERR_NOT_FOUND");

        // 6.5 CORS Preflight OPTIONS -> 200 OK kèm Access-Control-Allow-Origin
        webTestClient.options().uri("/api/v1/events/public")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
    }
}
