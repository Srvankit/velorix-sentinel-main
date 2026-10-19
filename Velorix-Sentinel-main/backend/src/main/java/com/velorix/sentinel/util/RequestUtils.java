package com.velorix.sentinel.util;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Helpers for safely extracting client metadata from an incoming request.
 */
public final class RequestUtils {

    private static final List<String> IP_HEADERS = List.of(
            "X-Forwarded-For", "X-Real-IP", "CF-Connecting-IP");

    private static final int MAX_USER_AGENT_LENGTH = 255;

    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                return (comma > 0 ? value.substring(0, comma) : value).trim();
            }
        }
        return request.getRemoteAddr();
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
