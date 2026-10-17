package vn.eventplatform.auth.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.eventplatform.auth.service.TokenBlacklistService;
import vn.eventplatform.auth.service.TokenProvider;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenVerificationFilter extends OncePerRequestFilter {

    private final TokenProvider tokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                Claims claims = tokenProvider.parseToken(token);
                String userIdStr = claims.getSubject();
                UUID userId = UUID.fromString(userIdStr);
                String jti = claims.getId();
                Number iatMsClaim = claims.get("iat_ms", Number.class);
                java.util.Date effectiveIssuedAt = iatMsClaim != null
                        ? new java.util.Date(iatMsClaim.longValue())
                        : claims.getIssuedAt();

                if (tokenBlacklistService.isTokenRevoked(jti, userId, effectiveIssuedAt)) {
                    log.warn("Rejected revoked/blacklisted token jti={} for user={}", jti, userId);
                    SecurityContextHolder.clearContext();
                } else {
                    @SuppressWarnings("unchecked")
                    List<String> roles = claims.get("roles", List.class);
                    List<SimpleGrantedAuthority> authorities = roles != null
                            ? roles.stream()
                            .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                            .map(SimpleGrantedAuthority::new)
                            .toList()
                            : Collections.emptyList();

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userIdStr, token, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                log.warn("Invalid JWT presented to auth-service: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
