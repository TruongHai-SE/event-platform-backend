package vn.eventplatform.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Configuration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonOpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "BearerAuth";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_TRACEPARENT = "traceparent";
    public static final String MDC_REQUEST_ID_KEY = "requestId";

    private static final Pattern SAFE_REQUEST_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,64}$");

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI eventPlatformOpenAPI(
            @Value("${spring.application.name:event-platform-service}") String applicationName) {
        return new OpenAPI()
                .info(new Info()
                        .title("Event & Festival Management Platform - " + applicationName)
                        .version("1.0.0")
                        .description("Tài liệu chuẩn OpenAPI 3.0 cho hệ thống quản lý Sự kiện & Lễ hội")
                        .contact(new Contact()
                                .name("Group 6 — MSS301")
                                .email("support@eventplatform.vn")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Nhập JWT Access Token (không cần gõ chữ Bearer)")));
    }

    /**
     * Servlet CorrelationIdFilter: Propagates X-Request-Id & traceparent into SLF4J MDC
     * and guarantees MDC cleanup in finally block to prevent thread-pool MDC leakage.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnMissingBean(name = "commonCorrelationIdFilter")
    public OncePerRequestFilter commonCorrelationIdFilter() {
        return new CorrelationIdFilter();
    }

    public static class CorrelationIdFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            String incomingReqId = request.getHeader(HEADER_REQUEST_ID);
            String requestId = (incomingReqId != null && SAFE_REQUEST_ID_PATTERN.matcher(incomingReqId.trim()).matches())
                    ? incomingReqId.trim()
                    : UUID.randomUUID().toString();
            String traceparent = request.getHeader(HEADER_TRACEPARENT);

            MDC.put(MDC_REQUEST_ID_KEY, requestId);
            MDC.put(HEADER_REQUEST_ID, requestId);
            if (traceparent != null && !traceparent.isBlank()) {
                MDC.put(HEADER_TRACEPARENT, traceparent.trim());
            }
            response.setHeader(HEADER_REQUEST_ID, requestId);

            try {
                filterChain.doFilter(request, response);
            } finally {
                MDC.remove(MDC_REQUEST_ID_KEY);
                MDC.remove(HEADER_REQUEST_ID);
                MDC.remove(HEADER_TRACEPARENT);
            }
        }
    }
}
