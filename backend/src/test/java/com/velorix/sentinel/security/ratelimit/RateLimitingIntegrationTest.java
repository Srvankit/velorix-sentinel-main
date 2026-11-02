package com.velorix.sentinel.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "velorix.ratelimit.enabled=true",
        "velorix.ratelimit.login-ip-capacity=5",
        "velorix.ratelimit.login-email-capacity=10",
        "velorix.ratelimit.register-ip-capacity=3",
        "velorix.ratelimit.forgot-password-capacity=3",
        "velorix.ratelimit.general-api-capacity=120"
})
class RateLimitingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService.reset();
    }

    @Test
    @DisplayName("Task 3.7 Verification: 5 rapid login attempts succeed/proceed; 6th returns HTTP 429 with Retry-After")
    void loginIpLimitThrottlesAtSixthAttempt() throws Exception {
        String clientIp = "198.51.100.10";
        Map<String, String> body = Map.of("email", "testuser@example.com", "password", "InvalidPassword123!");
        String jsonPayload = objectMapper.writeValueAsString(body);

        // Requests 1 to 5 pass rate limiting (fail authentication with 401 Bad credentials)
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(req -> {
                                req.setRemoteAddr(clientIp);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt must be rejected with 429 Too Many Requests BEFORE reaching controller
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> {
                            req.setRemoteAddr(clientIp);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."))
                .andExpect(jsonPath("$.errors[0].code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("Rate limit exceeded")));
    }

    @Test
    @DisplayName("Login rate limiting applies identically to both /api/auth/login and /api/v1/auth/login")
    void loginThrottlingAppliesToLegacyAuthPrefix() throws Exception {
        String clientIp = "198.51.100.20";
        Map<String, String> body = Map.of("email", "legacy@example.com", "password", "InvalidPassword123!");
        String jsonPayload = objectMapper.writeValueAsString(body);

        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(req -> {
                                req.setRemoteAddr(clientIp);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt to /api/auth/login returns 429
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> {
                            req.setRemoteAddr(clientIp);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    @DisplayName("Login target email tier: distributed attacks across varying IPs throttled at 11th attempt")
    void loginEmailTierThrottlesDistributedCredentialStuffing() throws Exception {
        String targetEmail = "stuffing-victim@example.com";
        Map<String, String> body = Map.of("email", targetEmail, "password", "WrongGuess123!");
        String jsonPayload = objectMapper.writeValueAsString(body);

        // 10 attempts from 10 different client IPs (each IP has made only 1 request, so IP limit is not hit)
        for (int i = 1; i <= 10; i++) {
            String ip = "172.20.0." + i;
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(req -> {
                                req.setRemoteAddr(ip);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isUnauthorized());
        }

        // 11th attempt from a brand-new IP (172.20.0.99) targeting the same email must return 429
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> {
                            req.setRemoteAddr("172.20.0.99");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errors[0].code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("Registration tier: throttles at 4th attempt per IP")
    void registrationThrottledAtFourthAttempt() throws Exception {
        String clientIp = "198.51.100.30";
        Map<String, String> body = Map.of(
                "email", "reg-test@example.com",
                "password", "Str0ng!Passw0rd",
                "confirmPassword", "Str0ng!Passw0rd",
                "firstName", "John",
                "lastName", "Doe"
        );
        String jsonPayload = objectMapper.writeValueAsString(body);

        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/auth/register")
                            .with(req -> {
                                req.setRemoteAddr(clientIp);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isCreated());
        }

        // 4th registration from same IP is blocked with 429
        mockMvc.perform(post("/api/auth/register")
                        .with(req -> {
                            req.setRemoteAddr(clientIp);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.errors[0].code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("Forgot password tier: throttles at 4th request per IP/email")
    void forgotPasswordThrottledAtFourthAttempt() throws Exception {
        String clientIp = "198.51.100.40";
        Map<String, String> body = Map.of("email", "recovery@example.com");
        String jsonPayload = objectMapper.writeValueAsString(body);

        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/auth/forgot-password")
                            .with(req -> {
                                req.setRemoteAddr(clientIp);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk());
        }

        // 4th forgot-password request is blocked with 429
        mockMvc.perform(post("/api/auth/forgot-password")
                        .with(req -> {
                            req.setRemoteAddr(clientIp);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    @DisplayName("Task 3.8 + 3.7 Integration: Untrusted client IP spoofing via X-Forwarded-For cannot bypass rate limits")
    void untrustedClientCannotBypassRateLimitingWithSpoofedHeaders() throws Exception {
        String untrustedRemoteIp = "203.0.113.88";
        Map<String, String> body = Map.of("email", "spoof@example.com", "password", "InvalidPassword123!");
        String jsonPayload = objectMapper.writeValueAsString(body);

        // Attacker rotates X-Forwarded-For on each request
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(req -> {
                                req.setRemoteAddr(untrustedRemoteIp);
                                return req;
                            })
                            .header("X-Forwarded-For", "8.8.8." + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isUnauthorized());
        }

        // Even with a brand new spoofed X-Forwarded-For header, request 6 is throttled
        // because RequestUtils.clientIp resolves the untrusted socket address (203.0.113.88)
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> {
                            req.setRemoteAddr(untrustedRemoteIp);
                            return req;
                        })
                        .header("X-Forwarded-For", "9.9.9.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errors[0].code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("Exempt endpoints: OPTIONS preflights and health checks bypass rate limiting")
    void exemptEndpointsBypassRateLimiting() throws Exception {
        // Preflight OPTIONS request
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isNoContent());

        // Health check endpoint
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/health"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Oversized authentication payload (>64KB) is rejected with 413 to protect memory")
    void oversizedAuthPayloadRejectedWith413() throws Exception {
        String largePadding = "a".repeat(70 * 1024); // 70 KB
        String oversizedJson = "{\"email\":\"oversized@example.com\",\"password\":\"" + largePadding + "\"}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedJson))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.errors[0].code").value("PAYLOAD_TOO_LARGE"));
    }
}

