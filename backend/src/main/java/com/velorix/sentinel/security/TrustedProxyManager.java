package com.velorix.sentinel.security;

import com.velorix.sentinel.util.RequestUtils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * Task 3.8: Trusted Proxy Verification and Client IP Spoofing Prevention.
 *
 * <p>Enforces strict reverse-proxy security following RFC 7239:
 * <ul>
 *   <li>Direct client connections cannot spoof their IP by sending forwarded headers.</li>
 *   <li>Forwarded headers ({@code X-Forwarded-For}, {@code X-Real-IP}, {@code CF-Connecting-IP})
 *       are ONLY evaluated if {@code request.getRemoteAddr()} matches an explicitly configured trusted proxy.</li>
 *   <li>When requests arrive through a trusted proxy, {@code X-Forwarded-For} is traversed
 *       from right to left, stopping at the first untrusted IP (the originating client).</li>
 *   <li>All IP address parsing is strictly literal to prevent DNS lookups or unbounded operations.</li>
 *   <li>Malformed IP addresses and invalid CIDRs are safely rejected.</li>
 * </ul>
 */
@Component
public class TrustedProxyManager {

    private static final Logger log = LoggerFactory.getLogger(TrustedProxyManager.class);

    private final String rawConfig;
    private final List<IpAddressMatcher> matchers;

    public TrustedProxyManager(@Value("${velorix.security.trusted-proxies:}") String trustedProxiesConfig) {
        this.rawConfig = trustedProxiesConfig != null ? trustedProxiesConfig.trim() : "";
        this.matchers = parseAndValidate(this.rawConfig);
    }

    @PostConstruct
    public void init() {
        RequestUtils.setTrustedProxyManager(this);
        if (matchers.isEmpty()) {
            log.info("TrustedProxyManager initialized with 0 trusted proxies (fail-closed: forwarded headers ignored).");
        } else {
            log.info("TrustedProxyManager initialized with {} trusted proxy rule(s).", matchers.size());
        }
    }

    /**
     * Resolves the true originating client IP address.
     *
     * @param request the incoming servlet request
     * @return the verified client IP, or null if request is null
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String remoteAddr = stripPortIfPresent(request.getRemoteAddr());
        if (remoteAddr == null || !isValidLiteralIp(remoteAddr)) {
            return request.getRemoteAddr();
        }

        // If the immediate connection does NOT come from a trusted proxy,
        // all forwarded headers MUST be ignored to prevent IP spoofing.
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        // The immediate peer is trusted. Traverse X-Forwarded-For right-to-left.
        List<String> forwardedIps = extractForwardedIps(request);
        if (!forwardedIps.isEmpty()) {
            for (int i = forwardedIps.size() - 1; i >= 0; i--) {
                String candidate = forwardedIps.get(i);
                if (!isValidLiteralIp(candidate)) {
                    continue; // Skip malformed tokens in the chain
                }
                if (!isTrustedProxy(candidate)) {
                    // First untrusted IP from the right is the originating client IP!
                    return candidate;
                }
            }

            // If all entries in X-Forwarded-For matched trusted proxies, return the leftmost valid IP.
            for (String candidate : forwardedIps) {
                if (isValidLiteralIp(candidate)) {
                    return candidate;
                }
            }
        }

        // If X-Forwarded-For is absent or empty, check X-Real-IP
        String realIp = extractSingleHeaderIp(request, "X-Real-IP");
        if (realIp != null) {
            return realIp;
        }

        // Next check CF-Connecting-IP
        String cfConnectingIp = extractSingleHeaderIp(request, "CF-Connecting-IP");
        if (cfConnectingIp != null) {
            return cfConnectingIp;
        }

        // Fallback to the immediate remote address
        return remoteAddr;
    }

    /**
     * Checks if the given IP address matches any configured trusted proxy.
     *
     * @param ipAddress literal IP address
     * @return true if trusted, false otherwise
     */
    public boolean isTrustedProxy(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank() || matchers.isEmpty()) {
            return false;
        }
        String cleanIp = stripPortIfPresent(ipAddress.trim());
        if (!isValidLiteralIp(cleanIp)) {
            return false;
        }
        for (IpAddressMatcher matcher : matchers) {
            if (matcher.matches(cleanIp)) {
                return true;
            }
        }
        return false;
    }

    public List<IpAddressMatcher> getMatchers() {
        return matchers;
    }

    public String getRawConfig() {
        return rawConfig;
    }

    /**
     * Extracts and tokenizes all X-Forwarded-For entries across all header occurrences.
     */
    private List<String> extractForwardedIps(HttpServletRequest request) {
        List<String> ips = new ArrayList<>();
        Enumeration<String> headers = request.getHeaders("X-Forwarded-For");
        if (headers == null) {
            return ips;
        }
        while (headers.hasMoreElements()) {
            String headerValue = headers.nextElement();
            if (headerValue != null && !headerValue.isBlank()) {
                String[] parts = headerValue.split(",");
                for (String part : parts) {
                    if (part == null || part.isBlank()) {
                        continue;
                    }
                    String trimmed = stripPortIfPresent(part.trim());
                    if (trimmed != null && !trimmed.isEmpty()) {
                        ips.add(trimmed);
                    }
                }
            }
        }
        return ips;
    }

    /**
     * Extracts a single IP from a header like X-Real-IP or CF-Connecting-IP.
     */
    private String extractSingleHeaderIp(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value)) {
            return null;
        }
        int comma = value.indexOf(',');
        String first = (comma >= 0 ? value.substring(0, comma) : value).trim();
        String cleaned = stripPortIfPresent(first);
        return isValidLiteralIp(cleaned) ? cleaned : null;
    }

    /**
     * Strips trailing port from IPv4 (e.g. 192.0.2.1:8080 -> 192.0.2.1)
     * and bracketed IPv6 (e.g. [2001:db8::1]:8080 -> 2001:db8::1).
     */
    public static String stripPortIfPresent(String ipOrHost) {
        if (ipOrHost == null || ipOrHost.isBlank()) {
            return null;
        }
        String s = ipOrHost.trim();

        // Bracketed IPv6: [2001:db8::1]:8080 or [2001:db8::1]
        if (s.startsWith("[")) {
            int closing = s.indexOf(']');
            if (closing > 0) {
                return s.substring(1, closing);
            }
        }

        // IPv4 with port: contains exactly one colon followed by digits
        int firstColon = s.indexOf(':');
        int lastColon = s.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon) {
            String potentialPort = s.substring(firstColon + 1);
            if (isDigitsOnly(potentialPort)) {
                return s.substring(0, firstColon);
            }
        }

        return s;
    }

    /**
     * Validates that the provided string is a strictly literal IPv4 or IPv6 address.
     * Prevents DNS resolution by rejecting hostnames and arbitrary characters.
     */
    public static boolean isValidLiteralIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String s = stripPortIfPresent(ip.trim());
        if (s == null || s.isEmpty()) {
            return false;
        }
        if (isIpv4Literal(s)) {
            return true;
        }
        return isIpv6Literal(s);
    }

    private static boolean isIpv4Literal(String ip) {
        int len = ip.length();
        if (len < 7 || len > 15) {
            return false;
        }
        int dots = 0;
        int part = -1;
        for (int i = 0; i < len; i++) {
            char c = ip.charAt(i);
            if (c == '.') {
                if (part < 0 || part > 255) {
                    return false;
                }
                dots++;
                part = -1;
            } else if (c >= '0' && c <= '9') {
                if (part == -1) {
                    part = c - '0';
                } else {
                    part = part * 10 + (c - '0');
                    if (part > 255) {
                        return false;
                    }
                }
            } else {
                return false;
            }
        }
        return dots == 3 && part >= 0 && part <= 255;
    }

    private static boolean isIpv6Literal(String ip) {
        if (ip.indexOf(':') == -1) {
            return false;
        }
        // Disallow characters that cannot appear in IPv6 literals
        for (int i = 0; i < ip.length(); i++) {
            char c = ip.charAt(i);
            if (c != ':' && c != '.' && c != '%' && Character.digit(c, 16) == -1) {
                return false;
            }
        }
        // Verify via JDK parser without DNS: JDK only calls DNS if string is a hostname without colons.
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr instanceof Inet6Address;
        } catch (UnknownHostException | SecurityException e) {
            return false;
        }
    }

    private static boolean isDigitsOnly(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /**
     * Parses and validates trusted proxy definitions at startup.
     * Throws IllegalArgumentException on invalid IP or malformed CIDR.
     */
    private static List<IpAddressMatcher> parseAndValidate(String config) {
        if (config == null || config.isBlank()) {
            return Collections.emptyList();
        }

        List<IpAddressMatcher> list = new ArrayList<>();
        String[] tokens = config.split(",");
        for (String rawToken : tokens) {
            String token = rawToken.trim();
            if (token.isEmpty()) {
                continue;
            }

            if (token.contains("/")) {
                String[] parts = token.split("/");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Malformed trusted proxy CIDR notation: " + token);
                }
                String ipPart = parts[0].trim();
                String maskPart = parts[1].trim();

                if (!isValidLiteralIp(ipPart)) {
                    throw new IllegalArgumentException("Invalid IP address in trusted proxy CIDR definition: " + token);
                }
                if (!isDigitsOnly(maskPart)) {
                    throw new IllegalArgumentException("Invalid prefix length in trusted proxy CIDR definition: " + token);
                }

                int prefix;
                try {
                    prefix = Integer.parseInt(maskPart);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid prefix length in trusted proxy CIDR definition: " + token, e);
                }

                boolean isV4 = isIpv4Literal(ipPart);
                if (isV4 && (prefix < 0 || prefix > 32)) {
                    throw new IllegalArgumentException("Invalid CIDR prefix length for IPv4 (must be 0-32): " + token);
                }
                if (!isV4 && (prefix < 0 || prefix > 128)) {
                    throw new IllegalArgumentException("Invalid CIDR prefix length for IPv6 (must be 0-128): " + token);
                }

                list.add(new IpAddressMatcher(token));
            } else {
                if (!isValidLiteralIp(token)) {
                    throw new IllegalArgumentException("Invalid trusted proxy IP address definition: " + token);
                }
                list.add(new IpAddressMatcher(token));
            }
        }

        return Collections.unmodifiableList(list);
    }
}

