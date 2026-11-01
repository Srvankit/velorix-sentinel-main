package com.velorix.sentinel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.velorix.sentinel.util.RequestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Task 3.8: Unit tests for TrustedProxyManager and client IP spoofing defense.
 */
class TrustedProxyManagerTest {

    @Nested
    @DisplayName("Direct Client Connections (No Trusted Proxies Configured)")
    class DirectConnectionsWithoutProxies {

        private final TrustedProxyManager manager = new TrustedProxyManager("");

        @Test
        @DisplayName("Direct request without forwarded headers returns remote socket address")
        void directRequestWithoutHeaders() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("198.51.100.23");

            String resolved = manager.resolveClientIp(request);
            assertThat(resolved).isEqualTo("198.51.100.23");
        }

        @Test
        @DisplayName("Direct request with forged X-Forwarded-For is ignored, returning remote address")
        void forgedXForwardedForIgnored() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("198.51.100.23");
            request.addHeader("X-Forwarded-For", "8.8.8.8");

            String resolved = manager.resolveClientIp(request);
            assertThat(resolved).isEqualTo("198.51.100.23");
        }

        @Test
        @DisplayName("Direct request with forged X-Real-IP is ignored, returning remote address")
        void forgedXRealIpIgnored() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("198.51.100.23");
            request.addHeader("X-Real-IP", "1.1.1.1");

            String resolved = manager.resolveClientIp(request);
            assertThat(resolved).isEqualTo("198.51.100.23");
        }

        @Test
        @DisplayName("Direct request with forged CF-Connecting-IP is ignored, returning remote address")
        void forgedCfConnectingIpIgnored() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("198.51.100.23");
            request.addHeader("CF-Connecting-IP", "1.0.0.1");

            String resolved = manager.resolveClientIp(request);
            assertThat(resolved).isEqualTo("198.51.100.23");
        }

        @Test
        @DisplayName("Direct request with all spoofed headers returns actual remote address")
        void allSpoofedHeadersIgnored() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("198.51.100.23");
            request.addHeader("X-Forwarded-For", "8.8.8.8, 9.9.9.9");
            request.addHeader("X-Real-IP", "1.1.1.1");
            request.addHeader("CF-Connecting-IP", "1.0.0.1");

            String resolved = manager.resolveClientIp(request);
            assertThat(resolved).isEqualTo("198.51.100.23");
        }
    }

    @Nested
    @DisplayName("Trusted Proxy Chains and Traversals")
    class TrustedProxyChains {

        // Trusted proxies: local proxy 10.0.0.1, internal network 10.0.0.0/8, and Cloudflare range 172.64.0.0/13
        private final TrustedProxyManager manager = new TrustedProxyManager("10.0.0.0/8, 172.64.0.0/13, 127.0.0.1, ::1");

        @Test
        @DisplayName("Request from trusted proxy extracts single client IP from X-Forwarded-For")
        void trustedProxyExtractsClientIp() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("X-Forwarded-For", "203.0.113.195");

            String resolved = manager.resolveClientIp(request);
            assertThat(resolved).isEqualTo("203.0.113.195");
        }

        @Test
        @DisplayName("Multiple trusted proxy hops traverse right-to-left until first untrusted IP")
        void multipleTrustedHopsTraverseRightToLeft() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1"); // Immediate peer (trusted 10.0.0.0/8)
            // Chain: Client (203.0.113.50) -> Cloudflare (172.64.1.25) -> Internal LB (10.0.0.2)
            request.addHeader("X-Forwarded-For", "203.0.113.50, 172.64.1.25, 10.0.0.2");

            String resolved = manager.resolveClientIp(request);
            // 10.0.0.2 is trusted, 172.64.1.25 is trusted, 203.0.113.50 is NOT trusted -> originating client!
            assertThat(resolved).isEqualTo("203.0.113.50");
        }

        @Test
        @DisplayName("Attacker attempting to spoof IP through trusted proxy cannot override actual client IP")
        void attackerCannotSpoofThroughTrustedProxy() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1"); // Immediate peer is trusted proxy
            // Attacker (198.51.100.10) sent "X-Forwarded-For: 1.1.1.1" to the trusted proxy.
            // Trusted proxy appended attacker's IP:
            request.addHeader("X-Forwarded-For", "1.1.1.1, 198.51.100.10");

            String resolved = manager.resolveClientIp(request);
            // Right-to-left inspection encounters 198.51.100.10 first, which is NOT trusted!
            // It stops immediately and resolves to 198.51.100.10, ignoring 1.1.1.1.
            assertThat(resolved).isEqualTo("198.51.100.10");
        }

        @Test
        @DisplayName("Trusted proxy with X-Real-IP is used when X-Forwarded-For is absent")
        void trustedProxyUsesXRealIpWhenXffAbsent() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("X-Real-IP", "203.0.113.44");

            String resolved = manager.resolveClientIp(request);
            assertThat(resolved).isEqualTo("203.0.113.44");
        }

        @Test
        @DisplayName("Trusted proxy with CF-Connecting-IP is used when XFF and X-Real-IP are absent")
        void trustedProxyUsesCfConnectingIp() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("CF-Connecting-IP", "203.0.113.88");

            String resolved = manager.resolveClientIp(request);
            assertThat(resolved).isEqualTo("203.0.113.88");
        }

        @Test
        @DisplayName("Trusted proxy with no forwarded headers returns proxy remote address")
        void trustedProxyWithoutHeadersReturnsRemoteAddr() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");

            String resolved = manager.resolveClientIp(request);
            assertThat(resolved).isEqualTo("10.0.0.1");
        }
    }

    @Nested
    @DisplayName("Invalid and Malformed Inputs")
    class InvalidInputs {

        private final TrustedProxyManager manager = new TrustedProxyManager("10.0.0.1");

        @Test
        @DisplayName("Malformed IP address in X-Forwarded-For is skipped without DNS lookup")
        void malformedIpInHeaderSkipped() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            // Chain contains invalid hostname and non-IP strings
            request.addHeader("X-Forwarded-For", "203.0.113.77, evil.domain.com, <script>alert(1)</script>");

            String resolved = manager.resolveClientIp(request);
            // Malformed strings skipped, valid untrusted IP 203.0.113.77 returned
            assertThat(resolved).isEqualTo("203.0.113.77");
        }

        @Test
        @DisplayName("Empty or blank forwarded headers are safely ignored")
        void emptyHeadersIgnored() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("X-Forwarded-For", "   ,  ,, ");

            String resolved = manager.resolveClientIp(request);
            assertThat(resolved).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("IP with port is correctly normalized")
        void ipWithPortNormalized() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1:443");
            request.addHeader("X-Forwarded-For", "203.0.113.99:54321");

            String resolved = manager.resolveClientIp(request);
            assertThat(resolved).isEqualTo("203.0.113.99");
        }

        @Test
        @DisplayName("Bracketed IPv6 with port is correctly normalized")
        void bracketedIpv6WithPortNormalized() {
            TrustedProxyManager v6Manager = new TrustedProxyManager("::1, 2001:db8::1");
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("[2001:db8::1]:8443");
            request.addHeader("X-Forwarded-For", "[2001:db8:cafe::1]:51234");

            String resolved = v6Manager.resolveClientIp(request);
            assertThat(resolved).isEqualTo("2001:db8:cafe::1");
        }

        @Test
        @DisplayName("Null request safely returns null")
        void nullRequestReturnsNull() {
            assertThat(manager.resolveClientIp(null)).isNull();
            assertThat(RequestUtils.clientIp(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Startup Configuration Validation")
    class ConfigurationValidation {

        @Test
        @DisplayName("Valid CIDR and IP configurations parse cleanly")
        void validConfiguration() {
            TrustedProxyManager mgr = new TrustedProxyManager("127.0.0.1, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, ::1, fd00::/8");
            assertThat(mgr.getMatchers()).hasSize(6);
        }

        @Test
        @DisplayName("Invalid IPv4 CIDR prefix (>32) throws IllegalArgumentException at startup")
        void invalidIpv4CidrPrefix() {
            assertThatThrownBy(() -> new TrustedProxyManager("10.0.0.0/33"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid CIDR prefix length for IPv4");
        }

        @Test
        @DisplayName("Invalid IPv6 CIDR prefix (>128) throws IllegalArgumentException at startup")
        void invalidIpv6CidrPrefix() {
            assertThatThrownBy(() -> new TrustedProxyManager("fd00::/129"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid CIDR prefix length for IPv6");
        }

        @Test
        @DisplayName("Malformed IP address in CIDR definition throws IllegalArgumentException at startup")
        void malformedIpInCidr() {
            assertThatThrownBy(() -> new TrustedProxyManager("999.0.0.0/24"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid IP address in trusted proxy CIDR definition");
        }

        @Test
        @DisplayName("Domain name in trusted proxy definition throws IllegalArgumentException at startup")
        void domainNameInTrustedProxiesRejected() {
            assertThatThrownBy(() -> new TrustedProxyManager("proxy.internal.velorix.io"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid trusted proxy IP address definition");
        }

        @Test
        @DisplayName("Non-numeric prefix length throws IllegalArgumentException at startup")
        void nonNumericPrefixRejected() {
            assertThatThrownBy(() -> new TrustedProxyManager("10.0.0.0/prefix"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid prefix length");
        }
    }

    @Nested
    @DisplayName("RequestUtils Integration")
    class RequestUtilsIntegration {

        @Test
        @DisplayName("RequestUtils delegates to active TrustedProxyManager")
        void requestUtilsDelegates() {
            TrustedProxyManager custom = new TrustedProxyManager("192.168.1.1");
            RequestUtils.setTrustedProxyManager(custom);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.1.1");
            request.addHeader("X-Forwarded-For", "198.51.100.5");

            assertThat(RequestUtils.clientIp(request)).isEqualTo("198.51.100.5");

            // Untrusted direct client
            MockHttpServletRequest untrusted = new MockHttpServletRequest();
            untrusted.setRemoteAddr("203.0.113.1");
            untrusted.addHeader("X-Forwarded-For", "1.1.1.1");

            assertThat(RequestUtils.clientIp(untrusted)).isEqualTo("203.0.113.1");
        }
    }
}

