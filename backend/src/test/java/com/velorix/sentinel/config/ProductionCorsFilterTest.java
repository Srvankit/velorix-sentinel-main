package com.velorix.sentinel.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.config.properties.CorsProperties;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("Task 6.1 — Production CORS Filter Unit Tests")
class ProductionCorsFilterTest {

    private ProductionCorsFilter filter;
    private CorsProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CorsProperties(
                List.of("https://velorixsentinel.netlify.app"),
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
                List.of("Authorization", "Content-Type"),
                List.of("Authorization"),
                true,
                3600L
        );
        filter = new ProductionCorsFilter(properties);
    }

    @Test
    @DisplayName("Preflight OPTIONS from authorized origin returns 204 with complete CORS headers")
    void preflightAuthorizedOrigin() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        request.addHeader("Origin", "https://velorixsentinel.netlify.app");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "Content-Type, Authorization");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://velorixsentinel.netlify.app");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.getHeader("Vary")).isEqualTo("Origin");
        assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("POST");
        assertThat(response.getHeader("Access-Control-Max-Age")).isEqualTo("3600");
    }

    @Test
    @DisplayName("Preflight OPTIONS from unauthorized origin (evil.netlify.app) does NOT set Allow-Origin")
    void preflightUnauthorizedNetlifyOrigin() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        request.addHeader("Origin", "https://evil.netlify.app");
        request.addHeader("Access-Control-Request-Method", "POST");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(response.getStatus()).isNotEqualTo(204);
    }

    @Test
    @DisplayName("Preflight from subdomain or suffix attack does NOT set Allow-Origin")
    void preflightSuffixOrSubdomainAttack() throws ServletException, IOException {
        String[] attacks = {
                "https://attacker-netlify.app",
                "https://velorixsentinel.netlify.app.evil.com",
                "http://velorixsentinel.netlify.app",
                "https://velorixsentinel.netlify.app:8443",
                "https://evil-lovable.app"
        };

        for (String attackOrigin : attacks) {
            MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
            request.addHeader("Origin", attackOrigin);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getHeader("Access-Control-Allow-Origin"))
                    .as("Origin %s must not be allowed", attackOrigin)
                    .isNull();
        }
    }

    @Test
    @DisplayName("Actual GET request from authorized origin sets Allow-Origin and delegates to chain")
    void actualRequestAuthorizedOrigin() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader("Origin", "https://velorixsentinel.netlify.app");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://velorixsentinel.netlify.app");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(filterChain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Actual GET request from unauthorized origin does not set Allow-Origin")
    void actualRequestUnauthorizedOrigin() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader("Origin", "https://evil.netlify.app");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(filterChain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Request without Origin header proceeds without CORS headers")
    void requestWithoutOriginHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(filterChain.getRequest()).isNotNull();
    }
}

