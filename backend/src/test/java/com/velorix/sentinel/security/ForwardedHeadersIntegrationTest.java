package com.velorix.sentinel.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Task 3.4: Verifies proxy header handling under reverse-proxy TLS termination.
 *
 * Confirms that {@code server.forward-headers-strategy: framework} correctly
 * extracts {@code X-Forwarded-Proto}, {@code X-Forwarded-Host}, and {@code X-Forwarded-Port}
 * to reconstruct secure HTTPS URLs for OAuth redirect URIs.
 */
@SpringBootTest
@ActiveProfiles("test")
class ForwardedHeadersIntegrationTest {

    @Autowired
    private Environment environment;

    @Autowired(required = false)
    private ForwardedHeaderFilter forwardedHeaderFilter;

    @Test
    @DisplayName("server.forward-headers-strategy is configured to framework")
    void forwardHeadersStrategyConfigured() {
        String strategy = environment.getProperty("server.forward-headers-strategy");
        assertThat(strategy).isEqualTo("framework");
    }

    @Test
    @DisplayName("ForwardedHeaderFilter unwraps X-Forwarded-Proto to https and isSecure=true")
    void forwardedHeaderFilterProcessesProtoAndHost() throws IOException, ServletException {
        ForwardedHeaderFilter filter = forwardedHeaderFilter != null ? forwardedHeaderFilter : new ForwardedHeaderFilter();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "sentinel.velorix.io");
        request.addHeader("X-Forwarded-Port", "443");

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<HttpServletRequest> processedRequest = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            processedRequest.set((HttpServletRequest) req);
        });

        assertThat(processedRequest.get()).isNotNull();
        assertThat(processedRequest.get().getScheme()).isEqualTo("https");
        assertThat(processedRequest.get().isSecure()).isTrue();
        assertThat(processedRequest.get().getServerName()).isEqualTo("sentinel.velorix.io");
        assertThat(processedRequest.get().getServerPort()).isEqualTo(443);
        assertThat(processedRequest.get().getRequestURL().toString())
                .isEqualTo("https://sentinel.velorix.io/login/oauth2/code/google");
    }

    @Test
    @DisplayName("Standard request without forwarded headers preserves original scheme and host")
    void standardRequestWithoutForwardedHeadersPreserved() throws IOException, ServletException {
        ForwardedHeaderFilter filter = forwardedHeaderFilter != null ? forwardedHeaderFilter : new ForwardedHeaderFilter();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8081);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<HttpServletRequest> processedRequest = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            processedRequest.set((HttpServletRequest) req);
        });

        assertThat(processedRequest.get()).isNotNull();
        assertThat(processedRequest.get().getScheme()).isEqualTo("http");
        assertThat(processedRequest.get().isSecure()).isFalse();
        assertThat(processedRequest.get().getServerName()).isEqualTo("localhost");
        assertThat(processedRequest.get().getServerPort()).isEqualTo(8081);
    }
}