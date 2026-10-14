package vn.eventplatform.ad.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Component
public class TokenVerificationFilter extends OncePerRequestFilter {
    private final SecretKey secretKey;

    public TokenVerificationFilter(@Value("${security.token.secret-key}") String secret) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        secretKey = Keys.hmacShaKeyFor(raw.length >= 32 ? raw : java.util.Arrays.copyOf(raw, 32));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = Jwts.parser().verifyWith(secretKey).build()
                        .parseSignedClaims(header.substring(7)).getPayload();
                List<String> roles = claims.get("roles", List.class);
                var authorities = roles == null ? Collections.<SimpleGrantedAuthority>emptyList()
                        : roles.stream().map(SimpleGrantedAuthority::new).toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities));
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
