package com.velorix.sentinel.config.properties;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly typed CORS configuration bound from {@code velorix.cors.*}.
 *
 * <p>Enforces strict exact-origin matching. Wildcard matching on multi-tenant
 * hosting domains is completely eliminated to protect authenticated endpoints
 * from unauthorized cross-origin requests.</p>
 */
@ConfigurationProperties(prefix = "velorix.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        Boolean allowCredentials,
        Long maxAge) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty()
                ? List.of("https://velorixsentinel.netlify.app")
                : allowedOrigins.stream()
                        .filter(Objects::nonNull)
                        .flatMap(origin -> Arrays.stream(origin.split(",")))
                        .map(CorsProperties::normalizeOrigin)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        allowedMethods = allowedMethods == null || allowedMethods.isEmpty()
                ? List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                : List.copyOf(allowedMethods);

        allowedHeaders = allowedHeaders == null || allowedHeaders.isEmpty()
                ? List.of("*")
                : List.copyOf(allowedHeaders);

        exposedHeaders = exposedHeaders == null ? List.of() : List.copyOf(exposedHeaders);
        allowCredentials = allowCredentials == null || allowCredentials;
        maxAge = maxAge == null ? 3600L : maxAge;
    }

    /**
     * Strictly normalizes and validates a fully qualified origin (scheme, host, optional port).
     * Rejects any wildcards (*), path segments, queries, or fragments.
     *
     * @param rawOrigin raw origin string
     * @return normalized origin (e.g. "https://velorixsentinel.netlify.app"), or {@code null} if invalid/wildcard
     */
    public static String normalizeOrigin(String rawOrigin) {
        if (rawOrigin == null || rawOrigin.isBlank()) {
            return null;
        }
        String trimmed = rawOrigin.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        // Wildcards on public or multi-tenant domains are strictly forbidden
        if (trimmed.contains("*")) {
            return null;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();

            if (scheme == null || host == null) {
                return null;
            }
            // Must have no path, query, or user-info
            if ((uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
                    || uri.getQuery() != null
                    || uri.getUserInfo() != null) {
                return null;
            }

            scheme = scheme.toLowerCase(Locale.ROOT);
            host = host.toLowerCase(Locale.ROOT);

            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }

            // Canonicalize default ports
            if (("http".equals(scheme) && port == 80)
                    || ("https".equals(scheme) && port == 443)
                    || port == -1) {
                return scheme + "://" + host;
            } else {
                return scheme + "://" + host + ":" + port;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Validates whether an incoming Origin header strictly matches one of the allowed origins.
     *
     * @param incomingOrigin origin header value sent by client
     * @return {@code true} if origin strictly matches an authorized origin; {@code false} otherwise
     */
    public boolean isAllowedOrigin(String incomingOrigin) {
        if (incomingOrigin == null || incomingOrigin.isBlank()) {
            return false;
        }
        String normalized = normalizeOrigin(incomingOrigin);
        if (normalized == null) {
            return false;
        }
        return allowedOrigins.contains(normalized);
    }
}
