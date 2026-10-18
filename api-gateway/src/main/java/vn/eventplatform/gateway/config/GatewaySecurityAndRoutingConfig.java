package vn.eventplatform.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Slf4j
@Configuration
public class GatewaySecurityAndRoutingConfig {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_ROLES = "X-Roles";
    public static final String HEADER_INTERNAL_SECRET = "X-Internal-Secret";
    public static final String HEADER_INTERNAL_CALL = "X-Internal-Call";
    public static final String HEADER_FORWARDED_PREFIX = "X-Forwarded-Prefix";
    public static final String HEADER_FORWARDED_AUTH = "X-Forwarded-Authorization";
    public static final String ATTR_VERIFIED_USER_ID = "gateway.verified.userId";

    private static final Pattern SAFE_REQUEST_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,64}$");
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final Set<String> PUBLIC_AUTH_POST_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/verify",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/login",
            "/api/v1/auth/google",
            "/api/v1/auth/oauth2/google",
            "/api/v1/auth/refresh",
            "/api/v1/auth/refresh-token",
            "/api/v1/auth/logout",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/request-unlock",
            "/api/v1/auth/unlock-account"
    );

    @Getter
    @Setter
    @Component
    @ConfigurationProperties(prefix = "gateway.rate-limit")
    public static class GatewayRateLimitProperties {
        private boolean enabled = true;
        private int authRequestsPerMinute = 20;
        private int userRequestsPerMinute = 120;
        private int anonRequestsPerMinute = 60;
    }

    @Component
    public static class GatewaySecurityStateService {
        private static final String BLACKLIST_JTI_PREFIX = "bl:jti:";
        private static final String BLACKLIST_USER_PREFIX = "bl:user:";
        private static final String BLACKLIST_USER_SUFFIX = ":revoked_at";

        private final ReactiveStringRedisTemplate reactiveRedisTemplate;
        private final Map<String, Instant> memoryBlacklistedJti = new ConcurrentHashMap<>();
        private final Map<String, Long> memoryUserRevokedAt = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> memoryRateCounters = new ConcurrentHashMap<>();

        public GatewaySecurityStateService(@Autowired(required = false) ReactiveStringRedisTemplate reactiveRedisTemplate) {
            this.reactiveRedisTemplate = reactiveRedisTemplate;
        }

        public void blacklistJtiInMemory(String jti, Duration ttl) {
            if (jti != null && !jti.isBlank()) {
                memoryBlacklistedJti.put(jti, Instant.now().plus(ttl));
            }
        }

        public void revokeAllUserTokensInMemory(UUID userId, long revokedAtEpochMillis) {
            if (userId != null) {
                memoryUserRevokedAt.put(userId.toString(), revokedAtEpochMillis);
            }
        }

        public void clearTestState() {
            memoryBlacklistedJti.clear();
            memoryUserRevokedAt.clear();
            memoryRateCounters.clear();
        }

        public Mono<Boolean> isTokenRevoked(String jti, String userId, Date issuedAt) {
            Instant now = Instant.now();
            memoryBlacklistedJti.entrySet().removeIf(e -> e.getValue().isBefore(now));

            if (jti != null && memoryBlacklistedJti.containsKey(jti)) {
                return Mono.just(true);
            }
            if (userId != null && issuedAt != null) {
                Long memRevokedAt = memoryUserRevokedAt.get(userId);
                if (memRevokedAt != null && issuedAt.getTime() <= memRevokedAt) {
                    return Mono.just(true);
                }
            }
            if (reactiveRedisTemplate == null) {
                return Mono.just(false);
            }

            Mono<Boolean> jtiCheck = (jti != null && !jti.isBlank())
                    ? reactiveRedisTemplate.hasKey(BLACKLIST_JTI_PREFIX + jti)
                            .timeout(Duration.ofMillis(250))
                            .onErrorReturn(false)
                    : Mono.just(false);

            Mono<Boolean> userCheck = (userId != null && !userId.isBlank() && issuedAt != null)
                    ? reactiveRedisTemplate.opsForValue()
                            .get(BLACKLIST_USER_PREFIX + userId + BLACKLIST_USER_SUFFIX)
                            .timeout(Duration.ofMillis(250))
                            .map(val -> {
                                try {
                                    return issuedAt.getTime() <= Long.parseLong(val);
                                } catch (NumberFormatException ex) {
                                    return false;
                                }
                            })
                            .defaultIfEmpty(false)
                            .onErrorReturn(false)
                    : Mono.just(false);

            return Mono.zip(jtiCheck, userCheck)
                    .map(tuple -> tuple.getT1() || tuple.getT2());
        }

        public Mono<Integer> incrementAndGetRateCounter(String bucketKey) {
            int localVal = memoryRateCounters.computeIfAbsent(bucketKey, k -> new AtomicInteger(0)).incrementAndGet();
            if (reactiveRedisTemplate == null) {
                return Mono.just(localVal);
            }
            return reactiveRedisTemplate.opsForValue().increment(bucketKey)
                    .flatMap(val -> {
                        if (val == 1L) {
                            return reactiveRedisTemplate.expire(bucketKey, Duration.ofSeconds(65)).thenReturn(val);
                        }
                        return Mono.just(val);
                    })
                    .map(Long::intValue)
                    .timeout(Duration.ofMillis(250))
                    .onErrorReturn(localVal);
        }
    }

    public static Mono<Void> writeErrorResponse(
            ServerWebExchange exchange,
            ObjectMapper objectMapper,
            HttpStatus status,
            String code,
            String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String reqId = exchange.getRequest().getHeaders().getFirst(HEADER_REQUEST_ID);
        if (reqId != null && !response.getHeaders().containsKey(HEADER_REQUEST_ID)) {
            response.getHeaders().set(HEADER_REQUEST_ID, reqId);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", code);
        body.put("errorCode", code);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            byte[] fallback = ("{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(fallback)));
        }
    }

    private static final Pattern ENCODED_TRAVERSAL_PATTERN =
            Pattern.compile("%(25)*(2e|2f|5c|00)", Pattern.CASE_INSENSITIVE);

    public static boolean isSuspiciousRawPath(String rawPath) {
        if (rawPath == null) {
            return false;
        }
        String lower = rawPath.toLowerCase();
        if (lower.contains("..")
                || lower.contains("//")
                || lower.contains("\\")
                || lower.contains(";")
                || ENCODED_TRAVERSAL_PATTERN.matcher(lower).find()) {
            return true;
        }
        for (int i = 0; i < rawPath.length(); i++) {
            char c = rawPath.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInternalEndpointPath(String normalizedPath, String rawPath) {
        String p = normalizedPath != null ? normalizedPath.toLowerCase() : "";
        String r = rawPath != null ? rawPath.toLowerCase() : "";
        return p.equals("/internal")
                || p.startsWith("/internal/")
                || p.contains("/internal/v1/")
                || r.equals("/internal")
                || r.startsWith("/internal/")
                || r.contains("/internal/v1/");
    }

    public static void stripSpoofedClientHeaders(HttpHeaders headers) {
        headers.remove(HEADER_USER_ID);
        headers.remove(HEADER_USER_EMAIL);
        headers.remove(HEADER_USER_ROLE);
        headers.remove(HEADER_USER_ROLES);
        headers.remove(HEADER_ROLES);
        headers.remove(HEADER_INTERNAL_SECRET);
        headers.remove(HEADER_INTERNAL_CALL);
        headers.remove(HEADER_FORWARDED_PREFIX);
        headers.remove(HEADER_FORWARDED_AUTH);
    }

    /**
     * Universal WebFilter (Highest Precedence): Applies to ALL requests (including /internal/**, 404s, /actuator/**)
     * before RoutePredicateHandlerMapping, ensuring X-Request-Id is always present, path traversal is blocked (400),
     * /internal/** is blocked (403), and spoofed X-User-* / X-Roles headers are stripped.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebFilter universalCorrelationAndSecurityWebFilter(ObjectMapper objectMapper) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String incomingReqId = request.getHeaders().getFirst(HEADER_REQUEST_ID);
            final String requestId = (incomingReqId != null && SAFE_REQUEST_ID_PATTERN.matcher(incomingReqId.trim()).matches())
                    ? incomingReqId.trim()
                    : UUID.randomUUID().toString();

            exchange.getResponse().getHeaders().set(HEADER_REQUEST_ID, requestId);
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().getHeaders().set(HEADER_REQUEST_ID, requestId);
                return Mono.empty();
            });

            String rawPath = request.getURI().getRawPath();
            String normalizedPath = request.getPath().value();

            if (isSuspiciousRawPath(rawPath)) {
                log.warn("Blocked path traversal / dirty URI attempt on Gateway: {}", rawPath);
                return writeErrorResponse(exchange, objectMapper, HttpStatus.BAD_REQUEST,
                        "ERR_BAD_REQUEST", "Đường dẫn truy cập không hợp lệ");
            }

            if (isInternalEndpointPath(normalizedPath, rawPath)) {
                log.warn("Blocked external access to internal endpoint: {}", normalizedPath);
                return writeErrorResponse(exchange, objectMapper, HttpStatus.FORBIDDEN,
                        "ERR_FORBIDDEN", "Không được phép truy cập trực tiếp API nội bộ qua Gateway");
            }

            ServerHttpRequest sanitizedRequest = request.mutate()
                    .headers(headers -> {
                        headers.set(HEADER_REQUEST_ID, requestId);
                        stripSpoofedClientHeaders(headers);
                    })
                    .build();

            return chain.filter(exchange.mutate().request(sanitizedRequest).build());
        };
    }

    /**
     * Filter 1 (Order = -200): Correlation ID (X-Request-Id) & Structured JSON Request Logging.
     */
    @Bean
    @Order(-200)
    public GlobalFilter correlationAndLoggingFilter() {
        return new CorrelationAndLoggingFilter();
    }

    public static class CorrelationAndLoggingFilter implements GlobalFilter, Ordered {
        @Override
        public int getOrder() {
            return -200;
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            long startNanos = System.nanoTime();
            ServerHttpRequest request = exchange.getRequest();
            String incomingReqId = request.getHeaders().getFirst(HEADER_REQUEST_ID);
            final String requestId = (incomingReqId != null && SAFE_REQUEST_ID_PATTERN.matcher(incomingReqId.trim()).matches())
                    ? incomingReqId.trim()
                    : UUID.randomUUID().toString();

            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(h -> h.set(HEADER_REQUEST_ID, requestId))
                    .build();
            exchange.getResponse().getHeaders().set(HEADER_REQUEST_ID, requestId);

            ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
            return chain.filter(mutatedExchange)
                    .doFinally(signalType -> {
                        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
                        HttpStatusCode statusCode = mutatedExchange.getResponse().getStatusCode();
                        int status = statusCode != null ? statusCode.value() : 200;
                        String userId = mutatedExchange.getAttributeOrDefault(ATTR_VERIFIED_USER_ID, "anonymous");
                        String clientIp = extractClientIp(mutatedExchange.getRequest());
                        log.info("{\"event\":\"gateway_access\",\"requestId\":\"{}\",\"method\":\"{}\",\"path\":\"{}\",\"status\":{},\"latencyMs\":{},\"clientIp\":\"{}\",\"userId\":\"{}\"}",
                                requestId,
                                mutatedRequest.getMethod(),
                                mutatedRequest.getURI().getRawPath(),
                                status,
                                latencyMs,
                                clientIp,
                                userId);
                    });
        }
    }

    /**
     * Filter 2 (Order = -190): Security Sanitizer & Internal Endpoint Blocker.
     * - Blocks path traversal (.., //, \, ;, %2e, %2f) and /internal/** access from outside.
     * - Strips spoofed X-User-*, X-Roles, and X-Internal-Secret headers sent by external clients.
     */
    @Bean
    @Order(-190)
    public GlobalFilter securitySanitizerFilter(ObjectMapper objectMapper) {
        return new SecuritySanitizerFilter(objectMapper);
    }

    public static class SecuritySanitizerFilter implements GlobalFilter, Ordered {
        private final ObjectMapper objectMapper;

        public SecuritySanitizerFilter(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public int getOrder() {
            return -190;
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            ServerHttpRequest request = exchange.getRequest();
            String rawPath = request.getURI().getRawPath();
            String normalizedPath = request.getPath().value();

            if (isSuspiciousRawPath(rawPath)) {
                log.warn("Blocked path traversal attempt on Gateway: {}", rawPath);
                return writeErrorResponse(exchange, objectMapper, HttpStatus.BAD_REQUEST,
                        "ERR_BAD_REQUEST", "Đường dẫn truy cập không hợp lệ");
            }

            if (isInternalEndpointPath(normalizedPath, rawPath)) {
                log.warn("Blocked external access to internal endpoint: {}", normalizedPath);
                return writeErrorResponse(exchange, objectMapper, HttpStatus.FORBIDDEN,
                        "ERR_FORBIDDEN", "Không được phép truy cập trực tiếp API nội bộ qua Gateway");
            }

            ServerHttpRequest sanitizedRequest = request.mutate()
                    .headers(GatewaySecurityAndRoutingConfig::stripSpoofedClientHeaders)
                    .build();

            return chain.filter(exchange.mutate().request(sanitizedRequest).build());
        }
    }

    /**
     * Filter 3 (Order = -150): Method-Aware Public Whitelist, JWT Signature/Expiry Validation,
     * Redis Blacklist Check, Role Guard & X-User-* / X-Roles Header Injection.
     */
    @Bean
    @Order(-150)
    public GlobalFilter jwtReactiveAuthenticationFilter(
            @Value("${security.token.secret-key}") String secret,
            GatewaySecurityStateService securityStateService,
            ObjectMapper objectMapper) {
        return new JwtReactiveAuthenticationFilter(secret, securityStateService, objectMapper);
    }

    public static class JwtReactiveAuthenticationFilter implements GlobalFilter, Ordered {
        private final SecretKey secretKey;
        private final GatewaySecurityStateService securityStateService;
        private final ObjectMapper objectMapper;

        public JwtReactiveAuthenticationFilter(
                String secret,
                GatewaySecurityStateService securityStateService,
                ObjectMapper objectMapper) {
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                byte[] padded = new byte[32];
                System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
                this.secretKey = Keys.hmacShaKeyFor(padded);
            } else {
                this.secretKey = Keys.hmacShaKeyFor(keyBytes);
            }
            this.securityStateService = securityStateService;
            this.objectMapper = objectMapper;
        }

        @Override
        public int getOrder() {
            return -150;
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            ServerHttpRequest request = exchange.getRequest();
            HttpMethod method = request.getMethod();
            String path = request.getPath().value();

            if (HttpMethod.OPTIONS.equals(method)) {
                return chain.filter(exchange);
            }

            boolean isPublic = isPublicEndpoint(method, path);
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                if (isPublic) {
                    return chain.filter(exchange);
                }
                return writeErrorResponse(exchange, objectMapper, HttpStatus.UNAUTHORIZED,
                        "ERR_UNAUTHORIZED", "Yêu cầu xác thực tài khoản (Thiếu Bearer Token)");
            }

            String token = authHeader.substring(7).trim();
            if (token.isEmpty()) {
                return writeErrorResponse(exchange, objectMapper, HttpStatus.UNAUTHORIZED,
                        "ERR_UNAUTHORIZED", "Bearer Token không được để trống");
            }

            final Claims claims;
            try {
                claims = Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            } catch (ExpiredJwtException ex) {
                return writeErrorResponse(exchange, objectMapper, HttpStatus.UNAUTHORIZED,
                        "ERR_TOKEN_EXPIRED", "Phiên đăng nhập đã hết hạn");
            } catch (JwtException | IllegalArgumentException ex) {
                return writeErrorResponse(exchange, objectMapper, HttpStatus.UNAUTHORIZED,
                        "ERR_TOKEN_INVALID", "Token xác thực không hợp lệ hoặc bị giả mạo");
            }

            String userId = claims.getSubject();
            String jti = claims.getId();
            Date issuedAt = claims.getIssuedAt();
            String email = claims.get("email", String.class);
            List<String> roles = extractRoles(claims);
            String primaryRole = roles.isEmpty() ? "ROLE_USER" : roles.get(0);
            String joinedRoles = String.join(",", roles);

            if (userId == null || userId.isBlank()) {
                return writeErrorResponse(exchange, objectMapper, HttpStatus.UNAUTHORIZED,
                        "ERR_TOKEN_INVALID", "Token thiếu định danh người dùng (sub)");
            }

            return securityStateService.isTokenRevoked(jti, userId, issuedAt)
                    .flatMap(revoked -> {
                        if (Boolean.TRUE.equals(revoked)) {
                            log.warn("Gateway rejected revoked token jti={} userId={}", jti, userId);
                            return writeErrorResponse(exchange, objectMapper, HttpStatus.UNAUTHORIZED,
                                    "ERR_TOKEN_EXPIRED", "Phiên đăng nhập đã hết hạn hoặc bị thu hồi");
                        }

                        if (isAdminOnlyEndpoint(path) && !hasAdminRole(roles)) {
                            log.warn("Gateway blocked non-admin user {} (roles={}) from accessing {}", userId, roles, path);
                            return writeErrorResponse(exchange, objectMapper, HttpStatus.FORBIDDEN,
                                    "ERR_FORBIDDEN", "Bạn không có quyền thực hiện thao tác quản trị này");
                        }

                        exchange.getAttributes().put(ATTR_VERIFIED_USER_ID, userId);
                        ServerHttpRequest enrichedRequest = exchange.getRequest().mutate()
                                .headers(h -> {
                                    h.set(HEADER_USER_ID, userId);
                                    if (email != null) {
                                        h.set(HEADER_USER_EMAIL, email);
                                    }
                                    h.set(HEADER_USER_ROLE, primaryRole);
                                    h.set(HEADER_USER_ROLES, joinedRoles);
                                    h.set(HEADER_ROLES, joinedRoles);
                                })
                                .build();

                        return chain.filter(exchange.mutate().request(enrichedRequest).build());
                    });
        }

        @SuppressWarnings("unchecked")
        private List<String> extractRoles(Claims claims) {
            List<String> result = new ArrayList<>();
            Object rolesClaim = claims.get("roles");
            if (rolesClaim instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null && !item.toString().isBlank()) {
                        result.add(item.toString().trim());
                    }
                }
            }
            String singleRole = claims.get("role", String.class);
            if (singleRole != null && !singleRole.isBlank() && !result.contains(singleRole.trim())) {
                result.add(singleRole.trim());
            }
            return result.isEmpty() ? Collections.singletonList("ROLE_USER") : result;
        }

        private boolean hasAdminRole(List<String> roles) {
            return roles.stream().anyMatch(r -> "ROLE_ADMIN".equalsIgnoreCase(r) || "ADMIN".equalsIgnoreCase(r));
        }

        public static boolean isAdminOnlyEndpoint(String path) {
            return PATH_MATCHER.match("/api/v1/admin/**", path)
                    || PATH_MATCHER.match("/api/v1/auth/security-policy", path)
                    || PATH_MATCHER.match("/api/v1/auth/security-policy/**", path)
                    || PATH_MATCHER.match("/api/v1/roles/**", path)
                    || PATH_MATCHER.match("/api/v1/roles", path)
                    || PATH_MATCHER.match("/api/v1/permissions/**", path)
                    || PATH_MATCHER.match("/api/v1/permissions", path)
                    || PATH_MATCHER.match("/api/v1/users/admin/**", path);
        }

        public static boolean isPublicEndpoint(HttpMethod method, String path) {
            if (method == null || path == null) {
                return false;
            }
            if (HttpMethod.OPTIONS.equals(method)) {
                return true;
            }
            if (HttpMethod.GET.equals(method)) {
                if (path.equals("/actuator/health")
                        || PATH_MATCHER.match("/actuator/health/**", path)
                        || path.equals("/actuator/info")
                        || PATH_MATCHER.match("/v3/api-docs/**", path)
                        || PATH_MATCHER.match("/swagger-ui/**", path)
                        || path.equals("/swagger-ui.html")
                        || PATH_MATCHER.match("/webjars/**", path)
                        || PATH_MATCHER.match("/uploads/**", path)) {
                    return true;
                }
                if (path.equals("/api/v1/events/public") || PATH_MATCHER.match("/api/v1/events/public/**", path)) {
                    return true;
                }
                if (path.equals("/api/v1/categories")
                        || (PATH_MATCHER.match("/api/v1/categories/*", path)
                            && !path.equals("/api/v1/categories/requests")
                            && !PATH_MATCHER.match("/api/v1/categories/requests/**", path))) {
                    return true;
                }
                if (path.equals("/api/v1/public/banners")
                        || PATH_MATCHER.match("/api/v1/public/banners/**", path)
                        || path.equals("/api/v1/public/ad-position-prices")
                        || PATH_MATCHER.match("/api/v1/public/ad-position-prices/**", path)) {
                    return true;
                }
                if (PATH_MATCHER.match("/api/v1/tickets/events/**", path)
                        || PATH_MATCHER.match("/api/v1/seat-maps/**", path)) {
                    return true;
                }
                if (path.equals("/api/v1/speaker-invitations/respond")
                        || PATH_MATCHER.match("/api/v1/speaker-invitations/respond/**", path)
                        || path.equals("/api/v1/payments/vnpay/callback")
                        || PATH_MATCHER.match("/api/v1/payments/vnpay/callback/**", path)) {
                    return true;
                }
            }
            if (HttpMethod.POST.equals(method)) {
                if (PUBLIC_AUTH_POST_PATHS.contains(path)) {
                    return true;
                }
                if (path.equals("/api/v1/speaker-invitations/respond")
                        || PATH_MATCHER.match("/api/v1/speaker-invitations/respond/**", path)
                        || PATH_MATCHER.match("/api/v1/payments/webhooks/**", path)
                        || path.equals("/api/v1/payments/vnpay-ipn")
                        || path.equals("/api/v1/payments/vnpay/callback")
                        || PATH_MATCHER.match("/api/v1/payments/vnpay/callback/**", path)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Filter 4 (Order = -120): Reactive Rate Limiting per IP (Auth & Anon) and per User.
     */
    @Bean
    @Order(-120)
    public GlobalFilter reactiveRateLimitFilter(
            GatewayRateLimitProperties properties,
            GatewaySecurityStateService securityStateService,
            ObjectMapper objectMapper) {
        return new ReactiveRateLimitFilter(properties, securityStateService, objectMapper);
    }

    public static class ReactiveRateLimitFilter implements GlobalFilter, Ordered {
        private final GatewayRateLimitProperties properties;
        private final GatewaySecurityStateService securityStateService;
        private final ObjectMapper objectMapper;

        public ReactiveRateLimitFilter(
                GatewayRateLimitProperties properties,
                GatewaySecurityStateService securityStateService,
                ObjectMapper objectMapper) {
            this.properties = properties;
            this.securityStateService = securityStateService;
            this.objectMapper = objectMapper;
        }

        @Override
        public int getOrder() {
            return -120;
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            if (!properties.isEnabled()) {
                return chain.filter(exchange);
            }
            String path = exchange.getRequest().getPath().value();
            if (path.startsWith("/actuator")) {
                return chain.filter(exchange);
            }

            long currentMinute = Instant.now().getEpochSecond() / 60;
            String clientIp = extractClientIp(exchange.getRequest());
            String verifiedUserId = exchange.getAttribute(ATTR_VERIFIED_USER_ID);

            final String bucketKey;
            final int limit;
            if (path.startsWith("/api/v1/auth/")) {
                bucketKey = "rate:auth:" + clientIp + ":" + currentMinute;
                limit = Math.max(1, properties.getAuthRequestsPerMinute());
            } else if (verifiedUserId != null && !verifiedUserId.isBlank()) {
                bucketKey = "rate:user:" + verifiedUserId + ":" + currentMinute;
                limit = Math.max(1, properties.getUserRequestsPerMinute());
            } else {
                bucketKey = "rate:ip:" + clientIp + ":" + currentMinute;
                limit = Math.max(1, properties.getAnonRequestsPerMinute());
            }

            return securityStateService.incrementAndGetRateCounter(bucketKey)
                    .flatMap(count -> {
                        int remaining = Math.max(0, limit - count);
                        exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(limit));
                        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(remaining));
                        if (count > limit) {
                            exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "60");
                            log.warn("Rate limit exceeded for bucket={} (count={}/{})", bucketKey, count, limit);
                            return writeErrorResponse(exchange, objectMapper, HttpStatus.TOO_MANY_REQUESTS,
                                    "ERR_TOO_MANY_REQUESTS", "Quá nhiều yêu cầu, vui lòng thử lại sau 60 giây");
                        }
                        return chain.filter(exchange);
                    });
        }
    }

    /**
     * Global Reactive Error Handler: Ensures 404, 503 (Downstream Down/Timeout), and 500
     * always return unified JSON ApiResponse instead of default HTML/Whitelabel error pages.
     */
    @Bean
    @Order(-2)
    public ErrorWebExceptionHandler gatewayGlobalErrorWebExceptionHandler(ObjectMapper objectMapper) {
        return (exchange, ex) -> {
            log.warn("Gateway handled error on {}: {}", exchange.getRequest().getURI().getRawPath(), ex.getMessage());
            if (ex instanceof ResponseStatusException rse) {
                HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
                if (status == HttpStatus.NOT_FOUND) {
                    return writeErrorResponse(exchange, objectMapper, HttpStatus.NOT_FOUND,
                            "ERR_NOT_FOUND", "Không tìm thấy đường dẫn API yêu cầu");
                }
                if (status == HttpStatus.SERVICE_UNAVAILABLE || status == HttpStatus.GATEWAY_TIMEOUT) {
                    return writeErrorResponse(exchange, objectMapper, HttpStatus.SERVICE_UNAVAILABLE,
                            "ERR_SERVICE_UNAVAILABLE", "Dịch vụ đích hiện đang bảo trì hoặc quá tải, vui lòng thử lại sau");
                }
                HttpStatus resolved = status != null ? status : HttpStatus.BAD_GATEWAY;
                return writeErrorResponse(exchange, objectMapper, resolved,
                        "ERR_" + resolved.name(), rse.getReason() != null ? rse.getReason() : "Lỗi xử lý tại cổng API Gateway");
            }
            if (isDownstreamConnectionFailure(ex)) {
                return writeErrorResponse(exchange, objectMapper, HttpStatus.SERVICE_UNAVAILABLE,
                        "ERR_SERVICE_UNAVAILABLE", "Dịch vụ đích tạm thời không phản hồi, vui lóng thử lại sau");
            }
            return writeErrorResponse(exchange, objectMapper, HttpStatus.INTERNAL_SERVER_ERROR,
                    "ERR_INTERNAL_SERVER", "Đã xảy ra lỗi hệ thống tại API Gateway, vui lòng thử lại sau");
        };
    }

    private static boolean isDownstreamConnectionFailure(Throwable ex) {
        Throwable curr = ex;
        while (curr != null) {
            if (curr instanceof ConnectException || curr instanceof TimeoutException
                    || curr.getClass().getName().contains("AnnotatedConnectException")
                    || curr.getClass().getName().contains("ReadTimeoutException")
                    || curr.getClass().getName().contains("PrematureCloseException")) {
                return true;
            }
            curr = curr.getCause();
        }
        return false;
    }

    public static String extractClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null && remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : "127.0.0.1";
    }

    @RestController
    public static class GatewayFallbackController {
        @RequestMapping("/fallback/{serviceName}")
        public Mono<ResponseEntity<Map<String, Object>>> fallback(@PathVariable("serviceName") String serviceName) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("code", "ERR_SERVICE_UNAVAILABLE");
            body.put("errorCode", "ERR_SERVICE_UNAVAILABLE");
            body.put("message", "Dịch vụ " + serviceName + " tạm thời gián đoạn, vui lòng thử lại sau");
            body.put("timestamp", Instant.now().toString());
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
        }
    }
}
