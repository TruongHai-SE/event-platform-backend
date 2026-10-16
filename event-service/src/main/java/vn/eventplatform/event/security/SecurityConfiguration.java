package vn.eventplatform.event.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final TokenVerificationFilter tokenVerificationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), "Chưa xác thực người dùng")
                            ));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    ApiResponse.error(ErrorCode.FORBIDDEN.getCode(), "Bạn không có quyền thực hiện thao tác này")
                            ));
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/internal/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/actuator/**",
                                "/uploads/**",
                                "/error"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/categories",
                                "/api/v1/categories/*",
                                "/api/v1/events/public/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(tokenVerificationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
