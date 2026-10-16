package vn.eventplatform.event.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class TokenVerificationFilter extends OncePerRequestFilter {

    private final SecretKey secretKey;

    public TokenVerificationFilter(@Value("${security.token.secret-key}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            this.secretKey = Keys.hmacShaKeyFor(padded);
        } else {
            this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String userId = claims.getSubject();
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);
                if (roles == null) {
                    String singleRole = claims.get("role", String.class);
                    if (StringUtils.hasText(singleRole)) {
                        roles = List.of(singleRole);
                    }
                }

                List<GrantedAuthority> authorities = buildAuthorities(roles);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                log.warn("Invalid token presented: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        } else {
            String gatewayUserId = request.getHeader("X-User-Id");
            if (StringUtils.hasText(gatewayUserId)) {
                String rolesHeader = request.getHeader("X-Roles");
                if (!StringUtils.hasText(rolesHeader)) {
                    rolesHeader = request.getHeader("X-User-Role");
                }
                List<String> roles = StringUtils.hasText(rolesHeader)
                        ? Arrays.stream(rolesHeader.split(",")).map(String::trim).filter(StringUtils::hasText).toList()
                        : Collections.emptyList();
                List<GrantedAuthority> authorities = buildAuthorities(roles);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(gatewayUserId.trim(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private List<GrantedAuthority> buildAuthorities(Collection<String> rawRoles) {
        if (rawRoles == null || rawRoles.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String role : rawRoles) {
            if (!StringUtils.hasText(role)) {
                continue;
            }
            String trimmed = role.trim().toUpperCase();
            normalized.add(trimmed);
            if (!trimmed.startsWith("ROLE_")) {
                normalized.add("ROLE_" + trimmed);
            } else {
                normalized.add(trimmed.substring(5));
            }
        }
        List<GrantedAuthority> authorities = new ArrayList<>(normalized.size());
        for (String auth : normalized) {
            authorities.add(new SimpleGrantedAuthority(auth));
        }
        return authorities;
    }
}
