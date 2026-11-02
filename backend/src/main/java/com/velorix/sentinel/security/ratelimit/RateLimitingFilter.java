package com.velorix.sentinel.security.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.sentinel.common.ApiError;
import com.velorix.sentinel.common.ApiResponse;
import com.velorix.sentinel.config.properties.RateLimitProperties;
import com.velorix.sentinel.security.UserPrincipal;
import com.velorix.sentinel.util.RequestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter enforcing rate limiting tiers before CPU-intensive work (such as BCrypt hashing)
 * or large capture uploads take place.
 *
 * <p>Uses {@link RequestUtils#clientIp(HttpServletRequest)} to guarantee un-spoofable client IP
 * resolution via trusted reverse proxies.</p>
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(
            RateLimiterService rateLimiterService,
            RateLimitProperties properties,
            ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!properties.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Exempt OPTIONS requests (CORS preflights)
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }

        // Normalize trailing slash (e.g. /api/v1/auth/login/ -> /api/v1/auth/login)
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }

        // Exempt health checks, Swagger/OpenAPI documentation, root, and non-API paths
        if (isExempt(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();

        // Tier 1: Login (POST /api/v1/auth/login, POST /api/auth/login)
        if (HttpMethod.POST.matches(method) && isLoginPath(uri)) {
            handleLogin(request, response, filterChain);
            return;
        }

        // Tier 2: Registration (POST /api/v1/auth/register, POST /api/auth/register)
        if (HttpMethod.POST.matches(method) && isRegisterPath(uri)) {
            handleRegister(request, response, filterChain);
            return;
        }

        // Tier 3: Forgot Password (POST /api/v1/auth/forgot-password, POST /api/auth/forgot-password)
        if (HttpMethod.POST.matches(method) && isForgotPasswordPath(uri)) {
            handleForgotPassword(request, response, filterChain);
            return;
        }

        // Tier 4: File Upload (POST /api/v1/files/upload)
        if (HttpMethod.POST.matches(method) && isFileUploadPath(uri)) {
            handleFileUpload(request, response, filterChain);
            return;
        }

        // Tier 5: General API endpoints (/api/**)
        if (uri.startsWith("/api/")) {
            handleGeneralApi(request, response, filterChain);
            return;
        }

        // Any other non-API request passes through
        filterChain.doFilter(request, response);
    }

    private void handleLogin(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = RequestUtils.clientIp(request);
        CachedBodyHttpServletRequest wrappedRequest = null;
        String email = null;

        if (isJsonRequest(request)) {
            try {
                wrappedRequest = new CachedBodyHttpServletRequest(request);
                email = extractEmail(wrappedRequest.getCachedBody());
            } catch (IllegalArgumentException ex) {
                writeErrorResponse(response, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", ex.getMessage());
                return;
            } catch (IOException ex) {
                log.warn("Could not cache login request body: {}", ex.getMessage());
            }
        }

        RateLimitResult result = rateLimiterService.tryConsumeLogin(clientIp, email);
        if (!result.allowed()) {
            writeRateLimitResponse(response, result.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(wrappedRequest != null ? wrappedRequest : request, response);
    }

    private void handleRegister(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = RequestUtils.clientIp(request);
        RateLimitResult result = rateLimiterService.tryConsumeRegister(clientIp);
        if (!result.allowed()) {
            writeRateLimitResponse(response, result.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void handleForgotPassword(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = RequestUtils.clientIp(request);
        CachedBodyHttpServletRequest wrappedRequest = null;
        String email = null;

        if (isJsonRequest(request)) {
            try {
                wrappedRequest = new CachedBodyHttpServletRequest(request);
                email = extractEmail(wrappedRequest.getCachedBody());
            } catch (IllegalArgumentException ex) {
                writeErrorResponse(response, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", ex.getMessage());
                return;
            } catch (IOException ex) {
                log.warn("Could not cache forgot-password request body: {}", ex.getMessage());
            }
        }

        RateLimitResult result = rateLimiterService.tryConsumeForgotPassword(clientIp, email);
        if (!result.allowed()) {
            writeRateLimitResponse(response, result.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(wrappedRequest != null ? wrappedRequest : request, response);
    }

    private void handleFileUpload(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String userOrIp = resolveUserOrIpKey(request);
        RateLimitResult result = rateLimiterService.tryConsumeFileUpload(userOrIp);
        if (!result.allowed()) {
            writeRateLimitResponse(response, result.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void handleGeneralApi(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String userOrIp = resolveUserOrIpKey(request);
        RateLimitResult result = rateLimiterService.tryConsumeGeneralApi(userOrIp);
        if (!result.allowed()) {
            writeRateLimitResponse(response, result.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveUserOrIpKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            Object principal = auth.getPrincipal();
            if (principal instanceof UserPrincipal userPrincipal && userPrincipal.id() != null) {
                return "user:" + userPrincipal.id();
            }
            if (auth.getName() != null && !auth.getName().isBlank() && !"anonymousUser".equals(auth.getName())) {
                return "user:" + auth.getName();
            }
        }
        String clientIp = RequestUtils.clientIp(request);
        return "ip:" + (clientIp != null ? clientIp : "unknown");
    }

    private boolean isLoginPath(String uri) {
        return uri.equals("/api/v1/auth/login") || uri.equals("/api/auth/login");
    }

    private boolean isRegisterPath(String uri) {
        return uri.equals("/api/v1/auth/register") || uri.equals("/api/auth/register");
    }

    private boolean isForgotPasswordPath(String uri) {
        return uri.equals("/api/v1/auth/forgot-password") || uri.equals("/api/auth/forgot-password");
    }

    private boolean isFileUploadPath(String uri) {
        return uri.equals("/api/v1/files/upload");
    }

    private boolean isExempt(String uri) {
        return uri.equals("/")
                || uri.equals("/error")
                || uri.equals("/api/health")
                || uri.startsWith("/api/health/")
                || uri.equals("/health")
                || uri.startsWith("/health/")
                || uri.startsWith("/actuator")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/swagger-ui")
                || uri.equals("/swagger-ui.html");
    }

    private boolean isJsonRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private String extractEmail(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode tree = objectMapper.readTree(body);
            if (tree != null && tree.has("email")) {
                JsonNode emailNode = tree.get("email");
                if (emailNode != null && emailNode.isTextual()) {
                    String email = emailNode.asText();
                    if (email != null && !email.isBlank()) {
                        return email.trim().toLowerCase(Locale.ROOT);
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to extract email from request JSON: {}", ex.getMessage());
        }
        return null;
    }

    private void writeRateLimitResponse(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Void> apiResponse = ApiResponse.error(
                "Too many requests. Please try again later.",
                HttpStatus.TOO_MANY_REQUESTS,
                List.of(ApiError.of("RATE_LIMIT_EXCEEDED", "Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds."))
        );

        objectMapper.writeValue(response.getWriter(), apiResponse);
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status, String errorCode, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Void> apiResponse = ApiResponse.error(
                message,
                status,
                List.of(ApiError.of(errorCode, message))
        );

        objectMapper.writeValue(response.getWriter(), apiResponse);
    }
}

