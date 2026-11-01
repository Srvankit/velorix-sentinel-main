package com.velorix.sentinel.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.service.ActivityLogService;
import com.velorix.sentinel.util.RequestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Task 3.8 Verification Tests:
 * Confirms that untrusted client requests cannot forge their IP address in audit logs or security checks,
 * and that TrustedProxyManager is correctly integrated into Spring Boot context and SecurityConfig.
 */
@SpringBootTest
@ActiveProfiles("test")
class TrustedProxyIntegrationTest {

    @Autowired
    private TrustedProxyManager trustedProxyManager;

    @Autowired
    private ActivityLogService activityLogService;

    @Test
    @DisplayName("TrustedProxyManager bean is wired in application context")
    void trustedProxyManagerBeanLoaded() {
        assertThat(trustedProxyManager).isNotNull();
        assertThat(RequestUtils.getTrustedProxyManager()).isSameAs(trustedProxyManager);
    }

    @Test
    @DisplayName("Task 3.8 Verification: Request with forged X-Forwarded-For from untrusted client records actual socket address")
    void untrustedClientCannotForgeIpInAuditLogs() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("198.51.100.50");
        request.addHeader("X-Forwarded-For", "1.1.1.1");
        request.addHeader("X-Real-IP", "1.1.1.1");
        request.addHeader("CF-Connecting-IP", "1.1.1.1");

        // RequestUtils.clientIp(request) is the method consumed by ActivityLogService and security filters
        String resolvedIp = RequestUtils.clientIp(request);

        // Must record actual socket address (198.51.100.50), NOT the forged 1.1.1.1
        assertThat(resolvedIp).isEqualTo("198.51.100.50");
        assertThat(resolvedIp).isNotEqualTo("1.1.1.1");
    }

    @Test
    @DisplayName("Rotating X-Forwarded-For headers from untrusted client does not change resolved IP")
    void rotatingHeadersCannotBypassIdentification() {
        String[] spoofAttempts = {"8.8.8.8", "1.1.1.1", "10.0.0.1", "172.16.0.1", "203.0.113.1"};

        for (String spoofedIp : spoofAttempts) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setRemoteAddr("198.51.100.75");
            request.addHeader("X-Forwarded-For", spoofedIp);

            String resolvedIp = RequestUtils.clientIp(request);
            assertThat(resolvedIp).isEqualTo("198.51.100.75");
        }
    }
}

