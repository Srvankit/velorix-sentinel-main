package com.velorix.sentinel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.sentinel.common.ApiError;
import com.velorix.sentinel.common.ApiResponse;
import com.velorix.sentinel.constants.MessageConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Returns the standard API envelope for unauthenticated access attempts.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        String uri = request.getRequestURI();
        if (uri != null && (uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs"))) {
            log.debug("Forbidden access attempt to Swagger/OpenAPI endpoint {} from unauthenticated client", uri);
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(
                    MessageConstants.FORBIDDEN,
                    HttpStatus.FORBIDDEN,
                    List.of(ApiError.of("FORBIDDEN", MessageConstants.FORBIDDEN))));
            return;
        }

        log.debug("Unauthorized request to {} {}: {}",
                request.getMethod(), uri, authException.getMessage());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(
                MessageConstants.UNAUTHORIZED,
                HttpStatus.UNAUTHORIZED,
                List.of(ApiError.of("UNAUTHORIZED", MessageConstants.UNAUTHORIZED))));
    }
}
