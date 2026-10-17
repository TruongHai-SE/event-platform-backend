package vn.eventplatform.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TokenBlacklistService {

    public static final String JTI_PREFIX = "bl:jti:";
    public static final String USER_REVOKED_AT_PREFIX = "bl:user:";
    public static final String USER_REVOKED_AT_SUFFIX = ":revoked_at";

    private final StringRedisTemplate redisTemplate;
    // ponytail: in-memory fallback map when Redis is unreachable in local unit tests
    private final Map<String, Instant> localJtiBlacklist = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> localUserRevokedAt = new ConcurrentHashMap<>();

    public TokenBlacklistService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public void blacklistJti(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank() || expiresAt == null) {
            return;
        }
        long ttlMillis = expiresAt.toEpochMilli() - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            return;
        }
        localJtiBlacklist.put(jti, expiresAt);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(JTI_PREFIX + jti, "1", Duration.ofMillis(ttlMillis));
            } catch (Exception e) {
                log.debug("Redis unavailable for JTI blacklist, using local fallback: {}", e.getMessage());
            }
        }
    }

    public void revokeAllUserTokens(UUID userId, long accessExpirationMs) {
        if (userId == null) {
            return;
        }
        Instant now = Instant.now();
        localUserRevokedAt.put(userId, now);
        if (redisTemplate != null) {
            try {
                String key = USER_REVOKED_AT_PREFIX + userId + USER_REVOKED_AT_SUFFIX;
                redisTemplate.opsForValue().set(
                        key,
                        String.valueOf(now.toEpochMilli()),
                        Duration.ofMillis(Math.max(accessExpirationMs, 60_000L))
                );
            } catch (Exception e) {
                log.debug("Redis unavailable for user revocation timestamp, using local fallback: {}", e.getMessage());
            }
        }
    }

    public boolean isTokenRevoked(String jti, UUID userId, Date issuedAt) {
        Instant now = Instant.now();
        if (jti != null && !jti.isBlank()) {
            Instant localExpiry = localJtiBlacklist.get(jti);
            if (localExpiry != null) {
                if (localExpiry.isAfter(now)) {
                    return true;
                }
                localJtiBlacklist.remove(jti);
            }
            if (redisTemplate != null) {
                try {
                    Boolean exists = redisTemplate.hasKey(JTI_PREFIX + jti);
                    if (Boolean.TRUE.equals(exists)) {
                        return true;
                    }
                } catch (Exception e) {
                    log.debug("Redis lookup failed for JTI {}: {}", jti, e.getMessage());
                }
            }
        }

        if (userId != null && issuedAt != null) {
            long issuedEpochMs = issuedAt.getTime();
            Instant localRevokedAt = localUserRevokedAt.get(userId);
            if (localRevokedAt != null && issuedEpochMs <= localRevokedAt.toEpochMilli()) {
                return true;
            }
            if (redisTemplate != null) {
                try {
                    String val = redisTemplate.opsForValue().get(USER_REVOKED_AT_PREFIX + userId + USER_REVOKED_AT_SUFFIX);
                    if (val != null) {
                        long revokedEpochMs = Long.parseLong(val);
                        if (issuedEpochMs <= revokedEpochMs) {
                            return true;
                        }
                    }
                } catch (Exception e) {
                    log.debug("Redis lookup failed for user revocation {}: {}", userId, e.getMessage());
                }
            }
        }
        return false;
    }
}
