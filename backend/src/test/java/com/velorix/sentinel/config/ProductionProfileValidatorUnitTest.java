package com.velorix.sentinel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.config.properties.CorsProperties;
import com.velorix.sentinel.config.properties.JwtProperties;
import com.velorix.sentinel.config.properties.StorageProperties;
import com.velorix.sentinel.exception.ProductionConfigurationException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit test suite verifying production profile validation rules and secret guardrails (Task 6.7).
 */
@DisplayName("Task 6.7 — Production Profile Validator Unit Tests (M14)")
class ProductionProfileValidatorUnitTest {

    // Valid 64-byte (512-bit) cryptographically random base64 key
    private static final String VALID_JWT_SECRET = Base64.getEncoder().encodeToString(new byte[64]);

    private static final String VALID_DB_URL = "jdbc:postgresql://db.projectref.supabase.co:5432/postgres";
    private static final String VALID_DB_PASSWORD = "SuperSecureProdPassword#2026!";
    private static final String VALID_FRONTEND_URL = "https://velorixsentinel.netlify.app";
    private static final List<String> VALID_CORS_ORIGINS = List.of("https://velorixsentinel.netlify.app");

    @Test
    @DisplayName("Valid production configuration yields zero violations")
    void validProductionConfigurationYieldsZeroViolations() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.url", VALID_DB_URL);
        env.setProperty("spring.datasource.password", VALID_DB_PASSWORD);
        env.setProperty("spring.flyway.clean-disabled", "true");
        env.setProperty("spring.mail.host", "smtp.sendgrid.net");

        JwtProperties jwtProps = new JwtProperties(VALID_JWT_SECRET, "velorix-sentinel", Duration.ofMinutes(15), Duration.ofDays(7), "Bearer ", "Authorization");
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, List.of("GET"), List.of("*"), List.of(), true, 3600L);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, Duration.ofHours(24), Duration.ofMinutes(30), Duration.ofHours(12));
        StorageProperties.S3Properties s3 = new StorageProperties.S3Properties("velorix-captures-staging", "us-east-1", null, null, null, true, "captures/");
        StorageProperties storageProps = new StorageProperties("s3", null, null, null, null, s3, 10, null);

        ObjectProvider<DataSourceProperties> dsProvider = mockObjectProvider(VALID_DB_URL, VALID_DB_PASSWORD);
        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps, dsProvider);

        List<String> violations = validator.validate(false);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Missing or blank JWT_SECRET is rejected")
    void missingJwtSecretIsRejected() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        JwtProperties jwtProps = new JwtProperties("", "velorix-sentinel", null, null, null, null);
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, null, null, null, null, null);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, null, null, null);
        StorageProperties storageProps = new StorageProperties("filesystem", null, null, null, null, null, null, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps, mockObjectProvider(VALID_DB_URL, VALID_DB_PASSWORD));

        List<String> violations = validator.validate(false);
        assertThat(violations).anyMatch(v -> v.contains("JWT_SECRET") && v.contains("missing or empty"));
    }

    @Test
    @DisplayName("Invalid Base64 JWT_SECRET is rejected")
    void invalidBase64JwtSecretIsRejected() {
        MockEnvironment env = new MockEnvironment();
        JwtProperties jwtProps = new JwtProperties("not-valid-base64!@#$%", "velorix-sentinel", null, null, null, null);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, null, null, null);
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, null, null, null, null, null);
        StorageProperties storageProps = new StorageProperties("filesystem", null, null, null, null, null, null, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps, mockObjectProvider(VALID_DB_URL, VALID_DB_PASSWORD));

        List<String> violations = validator.validate(false);
        assertThat(violations).anyMatch(v -> v.contains("JWT_SECRET") && v.contains("valid Base64"));
    }

    @Test
    @DisplayName("Short JWT_SECRET (< 64 bytes) is rejected")
    void shortJwtSecretIsRejected() {
        MockEnvironment env = new MockEnvironment();
        String shortSecret = Base64.getEncoder().encodeToString(new byte[32]);
        JwtProperties jwtProps = new JwtProperties(shortSecret, "velorix-sentinel", null, null, null, null);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, null, null, null);
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, null, null, null, null, null);
        StorageProperties storageProps = new StorageProperties("filesystem", null, null, null, null, null, null, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps, mockObjectProvider(VALID_DB_URL, VALID_DB_PASSWORD));

        List<String> violations = validator.validate(false);
        assertThat(violations).anyMatch(v -> v.contains("JWT_SECRET") && v.contains("minimum of 64 bytes"));
    }

    @Test
    @DisplayName("Known test or placeholder JWT_SECRET is rejected")
    void placeholderJwtSecretIsRejected() {
        MockEnvironment env = new MockEnvironment();
        byte[] keyBytes = "test-secret-for-velorix-sentinel-must-not-be-used-in-production-123456789".getBytes();
        String placeholderSecret = Base64.getEncoder().encodeToString(keyBytes);
        JwtProperties jwtProps = new JwtProperties(placeholderSecret, "velorix-sentinel", null, null, null, null);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, null, null, null);
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, null, null, null, null, null);
        StorageProperties storageProps = new StorageProperties("filesystem", null, null, null, null, null, null, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps, mockObjectProvider(VALID_DB_URL, VALID_DB_PASSWORD));

        List<String> violations = validator.validate(false);
        assertThat(violations).anyMatch(v -> v.contains("placeholder secret pattern"));
    }

    @Test
    @DisplayName("Localhost and H2 database URLs are rejected in production")
    void localOrH2DatabaseUrlIsRejected() {
        MockEnvironment env = new MockEnvironment();
        JwtProperties jwtProps = new JwtProperties(VALID_JWT_SECRET, "velorix-sentinel", null, null, null, null);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, null, null, null);
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, null, null, null, null, null);
        StorageProperties storageProps = new StorageProperties("filesystem", null, null, null, null, null, null, null);

        // Test localhost
        ProductionProfileValidator validatorLocal = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps,
                mockObjectProvider("jdbc:postgresql://localhost:5432/velorix", VALID_DB_PASSWORD));
        assertThat(validatorLocal.validate(false)).anyMatch(v -> v.contains("local host address"));

        // Test in-memory H2
        ProductionProfileValidator validatorH2 = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps,
                mockObjectProvider("jdbc:h2:mem:test", VALID_DB_PASSWORD));
        assertThat(validatorH2.validate(false)).anyMatch(v -> v.contains("in-memory H2 database"));
    }

    @Test
    @DisplayName("Insecure default database passwords are rejected")
    void insecureDefaultDatabasePasswordIsRejected() {
        MockEnvironment env = new MockEnvironment();
        JwtProperties jwtProps = new JwtProperties(VALID_JWT_SECRET, "velorix-sentinel", null, null, null, null);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, null, null, null);
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, null, null, null, null, null);
        StorageProperties storageProps = new StorageProperties("filesystem", null, null, null, null, null, null, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps,
                mockObjectProvider(VALID_DB_URL, "postgres"));

        List<String> violations = validator.validate(false);
        assertThat(violations).anyMatch(v -> v.contains("insecure default credential"));
    }

    @Test
    @DisplayName("Frontend base URL pointing to localhost is rejected")
    void localhostFrontendBaseUrlIsRejected() {
        MockEnvironment env = new MockEnvironment();
        JwtProperties jwtProps = new JwtProperties(VALID_JWT_SECRET, "velorix-sentinel", null, null, null, null);
        AppProperties appProps = new AppProperties("http://localhost:5173", null, null, null);
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, null, null, null, null, null);
        StorageProperties storageProps = new StorageProperties("filesystem", null, null, null, null, null, null, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps,
                mockObjectProvider(VALID_DB_URL, VALID_DB_PASSWORD));

        List<String> violations = validator.validate(false);
        assertThat(violations).anyMatch(v -> v.contains("velorix.app.frontend-base-url") && v.contains("localhost"));
    }

    @Test
    @DisplayName("Wildcard CORS origins on multi-tenant hosting platforms are rejected")
    void wildcardCorsOriginsAreRejected() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("velorix.cors.allowed-origins", "https://*.netlify.app");

        JwtProperties jwtProps = new JwtProperties(VALID_JWT_SECRET, "velorix-sentinel", null, null, null, null);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, null, null, null);
        CorsProperties corsProps = new CorsProperties(List.of("https://*.netlify.app"), null, null, null, null, null);
        StorageProperties storageProps = new StorageProperties("filesystem", null, null, null, null, null, null, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps,
                mockObjectProvider(VALID_DB_URL, VALID_DB_PASSWORD));

        List<String> violations = validator.validate(false);
        assertThat(violations).anyMatch(v -> v.contains("wildcard origin"));
    }

    @Test
    @DisplayName("S3 storage with missing bucket or region is rejected")
    void incompleteS3StorageConfigurationIsRejected() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("velorix.storage.s3.bucket", "");

        JwtProperties jwtProps = new JwtProperties(VALID_JWT_SECRET, "velorix-sentinel", null, null, null, null);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, null, null, null);
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, null, null, null, null, null);
        StorageProperties.S3Properties s3Props = new StorageProperties.S3Properties("bucket", "us-east-1", null, "key-only", null, true, null);
        StorageProperties storageProps = new StorageProperties("s3", null, null, null, null, s3Props, null, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps,
                mockObjectProvider(VALID_DB_URL, VALID_DB_PASSWORD));

        List<String> violations = validator.validate(false);
        assertThat(violations).anyMatch(v -> v.contains("access-key and secret-key must both be supplied"));
    }

    @Test
    @DisplayName("Validation error messages never expose secret values")
    void validationErrorMessagesNeverExposeSecrets() {
        MockEnvironment env = new MockEnvironment();
        String secretWithLeakRisk = "SensitiveSecretPassword123!";
        JwtProperties jwtProps = new JwtProperties("", null, null, null, null, null);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, null, null, null);
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, null, null, null, null, null);
        StorageProperties storageProps = new StorageProperties("filesystem", null, null, null, null, null, null, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps,
                mockObjectProvider("jdbc:postgresql://localhost:5432/db?user=postgres&password=" + secretWithLeakRisk, "postgres"));

        List<String> violations = validator.validate(false);
        for (String violation : violations) {
            assertThat(violation).doesNotContain(secretWithLeakRisk);
        }
    }

    @Test
    @DisplayName("EnforceThrows throws ProductionConfigurationException when violations exist")
    void enforceThrowsThrowsProductionConfigurationException() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        JwtProperties jwtProps = new JwtProperties("", null, null, null, null, null);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, null, null, null);
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, null, null, null, null, null);
        StorageProperties storageProps = new StorageProperties("filesystem", null, null, null, null, null, null, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps,
                mockObjectProvider(VALID_DB_URL, VALID_DB_PASSWORD));

        assertThatThrownBy(() -> validator.validate(true))
                .isInstanceOf(ProductionConfigurationException.class)
                .hasMessageContaining("Production configuration validation failed with");
    }

    @Test
    @DisplayName("Missing spring.mail.host in production is rejected without override")
    void missingMailHostInProductionIsRejected() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.url", VALID_DB_URL);
        env.setProperty("spring.datasource.password", VALID_DB_PASSWORD);
        env.setProperty("spring.flyway.clean-disabled", "true");
        // spring.mail.host omitted

        JwtProperties jwtProps = new JwtProperties(VALID_JWT_SECRET, "velorix-sentinel", Duration.ofMinutes(15), Duration.ofDays(7), "Bearer ", "Authorization");
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, List.of("GET"), List.of("*"), List.of(), true, 3600L);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, Duration.ofHours(24), Duration.ofMinutes(30), Duration.ofHours(12));
        StorageProperties.S3Properties s3 = new StorageProperties.S3Properties("velorix-captures-staging", "us-east-1", null, null, null, true, "captures/");
        StorageProperties storageProps = new StorageProperties("s3", null, null, null, null, s3, 10, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps,
                mockObjectProvider(VALID_DB_URL, VALID_DB_PASSWORD));

        List<String> violations = validator.validate(false);
        assertThat(violations).anyMatch(v -> v.contains("spring.mail.host (SMTP_HOST) is missing or blank"));
    }

    @Test
    @DisplayName("Missing spring.mail.host with ALLOW_LOGGING_EMAIL_FALLBACK=true logs warning and passes")
    void missingMailHostWithOverridePasses() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.url", VALID_DB_URL);
        env.setProperty("spring.datasource.password", VALID_DB_PASSWORD);
        env.setProperty("spring.flyway.clean-disabled", "true");
        env.setProperty("ALLOW_LOGGING_EMAIL_FALLBACK", "true");

        JwtProperties jwtProps = new JwtProperties(VALID_JWT_SECRET, "velorix-sentinel", Duration.ofMinutes(15), Duration.ofDays(7), "Bearer ", "Authorization");
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, List.of("GET"), List.of("*"), List.of(), true, 3600L);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, Duration.ofHours(24), Duration.ofMinutes(30), Duration.ofHours(12));
        StorageProperties.S3Properties s3 = new StorageProperties.S3Properties("velorix-captures-staging", "us-east-1", null, null, null, true, "captures/");
        StorageProperties storageProps = new StorageProperties("s3", null, null, null, null, s3, 10, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps,
                mockObjectProvider(VALID_DB_URL, VALID_DB_PASSWORD));

        List<String> violations = validator.validate(false);
        assertThat(violations).noneMatch(v -> v.contains("spring.mail.host"));
    }

    @Test
    @DisplayName("Filesystem storage in production is rejected without override")
    void filesystemStorageInProductionIsRejected() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.url", VALID_DB_URL);
        env.setProperty("spring.datasource.password", VALID_DB_PASSWORD);
        env.setProperty("spring.flyway.clean-disabled", "true");
        env.setProperty("spring.mail.host", "smtp.sendgrid.net");

        JwtProperties jwtProps = new JwtProperties(VALID_JWT_SECRET, "velorix-sentinel", Duration.ofMinutes(15), Duration.ofDays(7), "Bearer ", "Authorization");
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, List.of("GET"), List.of("*"), List.of(), true, 3600L);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, Duration.ofHours(24), Duration.ofMinutes(30), Duration.ofHours(12));
        StorageProperties storageProps = new StorageProperties("filesystem", "/var/captures", null, null, null, null, 10, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps,
                mockObjectProvider(VALID_DB_URL, VALID_DB_PASSWORD));

        List<String> violations = validator.validate(false);
        assertThat(violations).anyMatch(v -> v.contains("velorix.storage.provider is configured as 'filesystem' in production profile"));
    }

    @Test
    @DisplayName("Filesystem storage with ALLOW_FILESYSTEM_STORAGE=true logs warning and passes")
    void filesystemStorageWithOverridePasses() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.url", VALID_DB_URL);
        env.setProperty("spring.datasource.password", VALID_DB_PASSWORD);
        env.setProperty("spring.flyway.clean-disabled", "true");
        env.setProperty("spring.mail.host", "smtp.sendgrid.net");
        env.setProperty("ALLOW_FILESYSTEM_STORAGE", "true");

        JwtProperties jwtProps = new JwtProperties(VALID_JWT_SECRET, "velorix-sentinel", Duration.ofMinutes(15), Duration.ofDays(7), "Bearer ", "Authorization");
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, List.of("GET"), List.of("*"), List.of(), true, 3600L);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, Duration.ofHours(24), Duration.ofMinutes(30), Duration.ofHours(12));
        StorageProperties storageProps = new StorageProperties("filesystem", "/var/captures", null, null, null, null, 10, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps,
                mockObjectProvider(VALID_DB_URL, VALID_DB_PASSWORD));

        List<String> violations = validator.validate(false);
        assertThat(violations).noneMatch(v -> v.contains("velorix.storage.provider"));
    }

    @Test
    @DisplayName("S3 storage missing bucket in production is rejected")
    void s3StorageMissingBucketIsRejected() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("velorix.storage.s3.bucket", "   ");

        JwtProperties jwtProps = new JwtProperties(VALID_JWT_SECRET, "velorix-sentinel", null, null, null, null);
        CorsProperties corsProps = new CorsProperties(VALID_CORS_ORIGINS, null, null, null, null, null);
        AppProperties appProps = new AppProperties(VALID_FRONTEND_URL, null, null, null);
        StorageProperties.S3Properties s3 = new StorageProperties.S3Properties("", "us-east-1", null, null, null, true, null);
        StorageProperties storageProps = new StorageProperties("s3", null, null, null, null, s3, null, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps,
                mockObjectProvider(VALID_DB_URL, VALID_DB_PASSWORD));

        List<String> violations = validator.validate(false);
        assertThat(violations).anyMatch(v -> v.contains("velorix.storage.s3.bucket") && v.contains("required"));
    }

    @Test
    @DisplayName("afterSingletonsInstantiated does not throw when non-prod profile is active")
    void afterSingletonsInstantiatedDoesNotThrowInDevOrTest() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test"); // non-prod

        JwtProperties jwtProps = new JwtProperties("", null, null, null, null, null);
        AppProperties appProps = new AppProperties(null, null, null, null);
        CorsProperties corsProps = new CorsProperties(null, null, null, null, null, null);
        StorageProperties storageProps = new StorageProperties(null, null, null, null, null, null, null, null);

        ProductionProfileValidator validator = new ProductionProfileValidator(env, jwtProps, corsProps, storageProps, appProps,
                mockObjectProvider("jdbc:h2:mem:test", ""));

        // Should execute smoothly without throwing
        validator.afterSingletonsInstantiated();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<DataSourceProperties> mockObjectProvider(String url, String password) {
        ObjectProvider<DataSourceProperties> provider = mock(ObjectProvider.class);
        DataSourceProperties dsProps = new DataSourceProperties();
        dsProps.setUrl(url);
        dsProps.setPassword(password);
        when(provider.getIfAvailable()).thenReturn(dsProps);
        return provider;
    }
}
