package vn.eventplatform.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Component
public class TokenProvider {

    private final SecretKey secretKey;
    private final long accessExpirationMs;
    private final long refreshExpirationDays;

    public TokenProvider(
            @Value("${security.token.secret-key}") String secret,
            @Value("${security.token.access-expiration-ms:900000}") long accessExpirationMs,
            @Value("${security.token.refresh-expiration-days:7}") long refreshExpirationDays
    ) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            this.secretKey = Keys.hmacShaKeyFor(padded);
        } else {
            this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        }
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    public String generateAccessToken(UUID accountId, String email, String username, List<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessExpirationMs);

        return Jwts.builder()
                .subject(accountId.toString())
                .claim("email", email)
                .claim("username", username)
                .claim("roles", roles != null ? roles : Collections.emptyList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public String generateRawRefreshToken() {
        return UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public Instant calculateRefreshExpiry() {
        return Instant.now().plusSeconds(refreshExpirationDays * 24 * 60 * 60);
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
