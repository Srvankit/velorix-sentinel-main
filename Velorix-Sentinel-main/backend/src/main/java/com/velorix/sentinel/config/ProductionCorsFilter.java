package com.velorix.sentinel.config;

import com.velorix.sentinel.config.properties.CorsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Handles browser CORS before Spring Security processes an API request.
 *
 * <p>Some managed reverse proxies do not preserve the complete preflight
 * request. Handling OPTIONS explicitly keeps the production console usable
 * without weakening authentication or allowing arbitrary origins.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionCorsFilter extends OncePerRequestFilter {

    private final CorsProperties properties;

    public ProductionCorsFilter(CorsProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && isAllowedOrigin(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Expose-Headers", String.join(", ", properties.exposedHeaders()));

            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                response.setHeader("Access-Control-Allow-Methods", String.join(", ", properties.allowedMethods()));
                response.setHeader("Access-Control-Allow-Headers", requestedHeaders(request));
                response.setHeader("Access-Control-Max-Age", String.valueOf(properties.maxAge()));
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowedOrigin(String origin) {
        return properties.allowedOrigins().stream().anyMatch(configured -> matches(configured, origin));
    }

    private boolean matches(String configured, String origin) {
        String normalized = configured.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains("*")) {
            String suffix = normalized.substring(normalized.indexOf('*') + 1);
            return origin.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT));
        }
        return normalized.equalsIgnoreCase(origin);
    }

    private String requestedHeaders(HttpServletRequest request) {
        String requested = request.getHeader("Access-Control-Request-Headers");
        if (requested == null || requested.isBlank()) {
            return String.join(", ", properties.allowedHeaders());
        }
        return requested;
    }
}
