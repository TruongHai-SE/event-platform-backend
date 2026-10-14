package vn.eventplatform.ad.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.eventplatform.ad.security.TokenVerificationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {
    private final TokenVerificationFilter tokenVerificationFilter;

    @Bean
    SecurityFilterChain filter(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/public/**", "/internal/**", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(tokenVerificationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
