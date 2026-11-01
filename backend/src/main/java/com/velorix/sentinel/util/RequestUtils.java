package com.velorix.sentinel.util;

import com.velorix.sentinel.security.TrustedProxyManager;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Helpers for safely extracting client metadata from an incoming request.
 *
 * <p>Enforces trusted proxy verification on client IP extraction (Task 3.8)
 * to prevent IP spoofing attacks.</p>
 */
public final class RequestUtils {

    private static final int MAX_USER_AGENT_LENGTH = 255;

    private static volatile TrustedProxyManager trustedProxyManager = new TrustedProxyManager("");

    public static void setTrustedProxyManager(TrustedProxyManager manager) {
        trustedProxyManager = manager != null ? manager : new TrustedProxyManager("");
    }

    public static TrustedProxyManager getTrustedProxyManager() {
        return trustedProxyManager;
    }

    /**
     * Resolves the originating client IP address with trusted proxy verification.
     *
     * <p>If the immediate remote address is not in the configured trusted proxy list,
     * forwarded headers ({@code X-Forwarded-For}, {@code X-Real-IP}, {@code CF-Connecting-IP})
     * are strictly ignored, preventing client IP spoofing.</p>
     *
     * @param request the incoming HTTP request
     * @return the verified client IP, or null if request is null
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return trustedProxyManager.resolveClientIp(request);
    }

    /**
     * Resolves the originating client IP using an explicit {@link TrustedProxyManager}.
     * Useful for testing and custom-scoped resolution.
     */
    public static String clientIp(HttpServletRequest request, TrustedProxyManager manager) {
        if (request == null) {
            return null;
        }
        TrustedProxyManager active = manager != null ? manager : trustedProxyManager;
        return active.resolveClientIp(request);
    }

    public static String userAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String agent = request.getHeader("User-Agent");
        if (agent == null || agent.isBlank()) {
            return null;
        }
        return agent.length() > MAX_USER_AGENT_LENGTH ? agent.substring(0, MAX_USER_AGENT_LENGTH) : agent;
    }

    private RequestUtils() {
        throw new AssertionError("No instances allowed");
    }
}
