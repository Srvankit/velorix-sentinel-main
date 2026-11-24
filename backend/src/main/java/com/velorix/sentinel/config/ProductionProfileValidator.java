package com.velorix.sentinel.config;

import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.config.properties.CorsProperties;
import com.velorix.sentinel.config.properties.JwtProperties;
import com.velorix.sentinel.config.properties.StorageProperties;
import com.velorix.sentinel.exception.ProductionConfigurationException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Validates operational parameters, environment variables, and secret guardrails
 * when running under the production profile ({@code prod} or {@code production}).
 *
 * <p>Enforces fail-fast startup behavior if mandatory production settings are missing,
 * malformed, or falling back to unsafe development defaults. Crucially, secret values
 * are NEVER printed in validation errors or logged output.</p>
 */
@Component
public class ProductionProfileValidator implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ProductionProfileValidator.class);

    private static final Set<String> INSECURE_DEFAULT_PASSWORDS = Set.of(
            "postgres", "password", "admin", "root", "123456", "velorix", "secret");

    private static final Set<String> INSECURE_JWT_PLACEHOLDERS = Set.of(
            "test-secret", "placeholder", "changeme", "password", "0123456789", "insecure");

    private static final Set<String> DISALLOWED_CORS_WILDCARDS = Set.of(
            "*", "*.netlify.app", "*.lovable.app", "*.vercel.app", "*.render.com", "*.pages.dev", "*.supabase.co");

    private final Environment environment;
    private final JwtProperties jwtProperties;
    private final CorsProperties corsProperties;
    private final StorageProperties storageProperties;
    private final AppProperties appProperties;
    private final ObjectProvider<DataSourceProperties> dataSourcePropertiesProvider;

    public ProductionProfileValidator(
            Environment environment,
            JwtProperties jwtProperties,
            CorsProperties corsProperties,
            StorageProperties storageProperties,
            AppProperties appProperties,
            ObjectProvider<DataSourceProperties> dataSourcePropertiesProvider) {
        this.environment = environment;
        this.jwtProperties = jwtProperties;
        this.corsProperties = corsProperties;
        this.storageProperties = storageProperties;
        this.appProperties = appProperties;
        this.dataSourcePropertiesProvider = dataSourcePropertiesProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        boolean isProd = isProductionProfile();
        if (isProd) {
            log.info("Production profile active — executing production profile validation & secret guardrails...");
            validate(true);
            log.info("Production profile validation passed: All operational parameters and secret guardrails verified.");
        } else {
            log.debug("Production profile not active (active profiles: {}) — skipping strict production validation.",
                    Arrays.toString(environment.getActiveProfiles()));
        }
    }

    /**
     * Determines whether the active environment profiles contain {@code prod} or {@code production}.
     */
    public boolean isProductionProfile() {
        String[] profiles = environment.getActiveProfiles();
        return Arrays.stream(profiles)
                .map(String::trim)
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
    }

    /**
     * Performs validation of production parameters and secret guardrails.
     *
     * @param enforceThrows whether to throw {@link ProductionConfigurationException} if violations are found
     * @return the list of identified configuration violations
     * @throws ProductionConfigurationException if {@code enforceThrows} is true and violations exist
     */
    public List<String> validate(boolean enforceThrows) {
        List<String> violations = new ArrayList<>();

        validateJwtSecret(violations);
        validateDatabaseConfiguration(violations);
        validateFrontendBaseUrl(violations);
        validateCorsAllowedOrigins(violations);
        validateStorageConfiguration(violations);
        validateEmailConfiguration(violations);
        validateOpenApiGating(violations);
        validateFlywayConfiguration(violations);

        if (!violations.isEmpty() && enforceThrows) {
            throw new ProductionConfigurationException(violations);
        }
        return violations;
    }

    private void validateJwtSecret(List<String> violations) {
        String secret = jwtProperties != null ? jwtProperties.secret() : null;
        if (secret == null || secret.isBlank()) {
            violations.add("velorix.security.jwt.secret (JWT_SECRET) is missing or empty. A cryptographically random Base64 secret of at least 64 bytes is required.");
            return;
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException ex) {
            violations.add("velorix.security.jwt.secret (JWT_SECRET) must be a valid Base64 encoded string.");
            return;
        }

        if (decoded.length < 64) {
            violations.add("velorix.security.jwt.secret (JWT_SECRET) decoded byte length is " + decoded.length
                    + " bytes. A minimum of 64 bytes (512 bits) is required for HMAC-SHA512 security.");
            return;
        }

        String decodedText = new String(decoded, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        for (String placeholder : INSECURE_JWT_PLACEHOLDERS) {
            if (decodedText.contains(placeholder)) {
                violations.add("velorix.security.jwt.secret (JWT_SECRET) matches a known test or placeholder secret pattern. Cryptographically random key generation (openssl rand -base64 64) is required.");
                break;
            }
        }
    }

    private void validateDatabaseConfiguration(List<String> violations) {
        DataSourceProperties dsProps = dataSourcePropertiesProvider.getIfAvailable();
        String url = dsProps != null && dsProps.getUrl() != null ? dsProps.getUrl() : environment.getProperty("spring.datasource.url");
        String password = dsProps != null && dsProps.getPassword() != null ? dsProps.getPassword() : environment.getProperty("spring.datasource.password");

        if (url == null || url.isBlank()) {
            violations.add("spring.datasource.url (SUPABASE_DB_URL / DB_URL) is required in production.");
        } else {
            String lowerUrl = url.toLowerCase(Locale.ROOT).trim();
            if (lowerUrl.contains("jdbc:h2:")) {
                violations.add("spring.datasource.url points to an in-memory H2 database. Production requires PostgreSQL 16.");
            } else if (!lowerUrl.startsWith("jdbc:postgresql://")) {
                violations.add("spring.datasource.url must be a remote PostgreSQL JDBC URL starting with 'jdbc:postgresql://'. Found: " + sanitizeJdbcUrl(url));
            } else if (lowerUrl.contains("localhost") || lowerUrl.contains("127.0.0.1") || lowerUrl.contains("0.0.0.0") || lowerUrl.contains("::1")) {
                violations.add("spring.datasource.url points to a local host address. A production PostgreSQL instance (e.g. Supabase session pooler) is required.");
            }
        }

        if (password == null || password.isBlank()) {
            violations.add("spring.datasource.password (SUPABASE_DB_PASSWORD / DB_PASSWORD) is required in production.");
        } else {
            String lowerPass = password.toLowerCase(Locale.ROOT).trim();
            if (INSECURE_DEFAULT_PASSWORDS.contains(lowerPass)) {
                violations.add("spring.datasource.password must not use an insecure default credential.");
            }
        }
    }

    private void validateFrontendBaseUrl(List<String> violations) {
        String baseUrl = appProperties != null ? appProperties.frontendBaseUrl() : null;
        if (baseUrl == null || baseUrl.isBlank()) {
            violations.add("velorix.app.frontend-base-url (FRONTEND_BASE_URL) is required for password reset and email verification links.");
            return;
        }

        String lower = baseUrl.toLowerCase(Locale.ROOT).trim();
        if (!lower.startsWith("https://")) {
            violations.add("velorix.app.frontend-base-url (FRONTEND_BASE_URL) must use the HTTPS scheme. Found: " + baseUrl);
        }
        if (lower.contains("localhost") || lower.contains("127.0.0.1")) {
            violations.add("velorix.app.frontend-base-url points to localhost. The public web console origin (e.g. https://velorixsentinel.netlify.app) is required.");
        }
    }

    private void validateCorsAllowedOrigins(List<String> violations) {
        String rawOrigins = environment.getProperty("velorix.cors.allowed-origins");
        if (rawOrigins != null && (rawOrigins.contains("*") || rawOrigins.contains("%2A"))) {
            violations.add("velorix.cors.allowed-origins contains an insecure wildcard origin. Explicit exact origins are required.");
        }

        List<String> origins = corsProperties != null ? corsProperties.allowedOrigins() : null;
        if (origins == null || origins.isEmpty()) {
            if (rawOrigins == null || !rawOrigins.contains("*")) {
                violations.add("velorix.cors.allowed-origins (CORS_ALLOWED_ORIGINS) must specify at least one trusted production origin.");
            }
            return;
        }

        for (String origin : origins) {
            String lower = origin.toLowerCase(Locale.ROOT).trim();
            if (DISALLOWED_CORS_WILDCARDS.contains(lower) || lower.contains("*")) {
                violations.add("velorix.cors.allowed-origins contains an insecure wildcard origin: '" + origin + "'. Explicit exact origins are required.");
            } else if (!lower.startsWith("https://") && !lower.contains("localhost")) {
                violations.add("velorix.cors.allowed-origins entry '" + origin + "' must use HTTPS.");
            }
        }
    }

    private void validateStorageConfiguration(List<String> violations) {
        String provider = storageProperties != null ? storageProperties.provider() : "filesystem";
        boolean allowFilesystem = Boolean.TRUE.equals(environment.getProperty("velorix.storage.allow-filesystem-storage", Boolean.class))
                || Boolean.parseBoolean(environment.getProperty("ALLOW_FILESYSTEM_STORAGE", "false"));

        if ("s3".equalsIgnoreCase(provider)) {
            StorageProperties.S3Properties s3 = storageProperties != null ? storageProperties.s3() : null;
            String envBucket = environment.getProperty("velorix.storage.s3.bucket");
            String bucket = (envBucket != null) ? envBucket : (s3 != null ? s3.bucket() : null);
            if (bucket == null || bucket.isBlank()) {
                violations.add("velorix.storage.s3.bucket (S3_BUCKET_NAME / SUPABASE_STORAGE_BUCKET) is required when storage provider is 's3'.");
            }
            String envRegion = environment.getProperty("velorix.storage.s3.region");
            String region = (envRegion != null) ? envRegion : (s3 != null ? s3.region() : null);
            if (region == null || region.isBlank()) {
                violations.add("velorix.storage.s3.region (AWS_REGION / S3_REGION) is required when storage provider is 's3'.");
            }
            if (s3 != null) {
                if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
                    boolean pathStyleConfigured = environment.containsProperty("velorix.storage.s3.path-style-access")
                            || environment.containsProperty("S3_PATH_STYLE_ACCESS")
                            || s3.pathStyleAccess();
                    if (!pathStyleConfigured) {
                        violations.add("velorix.storage.s3.path-style-access (S3_PATH_STYLE_ACCESS) must be configured when custom S3 endpoint is specified.");
                    }
                }
                boolean hasAccess = s3.accessKey() != null && !s3.accessKey().isBlank();
                boolean hasSecret = s3.secretKey() != null && !s3.secretKey().isBlank();
                if (hasAccess != hasSecret) {
                    violations.add("velorix.storage.s3 access-key and secret-key must both be supplied if static credentials are used.");
                }
            }
        } else if ("filesystem".equalsIgnoreCase(provider)) {
            if (allowFilesystem) {
                log.warn("EMERGENCY OVERRIDE WARNING: velorix.storage.provider is set to 'filesystem' via ALLOW_FILESYSTEM_STORAGE=true. Captures on ephemeral container storage will be lost upon restart. S3-compatible object storage (Supabase Storage) is strongly recommended for production/staging.");
            } else {
                violations.add("velorix.storage.provider is configured as 'filesystem' in production profile. Ephemeral container storage will permanently lose user captures on container restarts. S3-compatible object storage (VELORIX_STORAGE_PROVIDER=s3) is required. Set ALLOW_FILESYSTEM_STORAGE=true only for temporary emergency overrides.");
            }
        }
    }

    private void validateEmailConfiguration(List<String> violations) {
        String mailHost = environment.getProperty("spring.mail.host");
        boolean allowLoggingFallback = Boolean.TRUE.equals(environment.getProperty("velorix.mail.allow-logging-fallback", Boolean.class))
                || Boolean.parseBoolean(environment.getProperty("ALLOW_LOGGING_EMAIL_FALLBACK", "false"));

        if (mailHost == null || mailHost.isBlank()) {
            if (allowLoggingFallback) {
                log.warn("EMERGENCY OVERRIDE WARNING: spring.mail.host (SMTP_HOST) is missing or blank in production profile, but ALLOW_LOGGING_EMAIL_FALLBACK=true override is active. Real registration and password reset emails will NOT be delivered!");
            } else {
                violations.add("spring.mail.host (SMTP_HOST) is missing or blank in production profile. Real transactional email delivery is required for user account verification and password resets. Configure SMTP credentials or set ALLOW_LOGGING_EMAIL_FALLBACK=true only for temporary emergency overrides.");
            }
        }
    }

    private void validateOpenApiGating(List<String> violations) {
        boolean swaggerUiEnabled = environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false);
        boolean apiDocsEnabled = environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false);

        if (swaggerUiEnabled || apiDocsEnabled) {
            log.warn("SECURITY ADVISORY: Swagger UI / OpenAPI documentation is enabled in production profile. Administrative role gating is strictly enforced.");
        }
    }

    private void validateFlywayConfiguration(List<String> violations) {
        boolean cleanDisabled = environment.getProperty("spring.flyway.clean-disabled", Boolean.class, true);
        if (!cleanDisabled) {
            violations.add("spring.flyway.clean-disabled must be true in production to prevent destructive clean actions.");
        }
    }

    private static String sanitizeJdbcUrl(String url) {
        if (url == null) return "null";
        int queryIndex = url.indexOf('?');
        return queryIndex > 0 ? url.substring(0, queryIndex) + "?[REDACTED]" : url;
    }
}
