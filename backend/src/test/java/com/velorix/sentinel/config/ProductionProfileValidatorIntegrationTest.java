package com.velorix.sentinel.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.config.properties.CorsProperties;
import com.velorix.sentinel.config.properties.JwtProperties;
import com.velorix.sentinel.config.properties.StorageProperties;
import com.velorix.sentinel.exception.ProductionConfigurationException;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Integration tests verifying Spring Boot production profile startup and fail-fast behavior (Task 6.7).
 */
@DisplayName("Task 6.7 — Production Profile Validator Integration Tests (M14)")
class ProductionProfileValidatorIntegrationTest {

    private static final String VALID_SECRET = Base64.getEncoder().encodeToString(new byte[64]);

    @Configuration
    @EnableConfigurationProperties({
            JwtProperties.class,
            CorsProperties.class,
            StorageProperties.class,
            AppProperties.class
    })
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class, ProductionProfileValidator.class);

    @Test
    @DisplayName("Context loads successfully when valid production configuration is provided")
    void contextLoadsWithValidProductionConfiguration() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "velorix.security.jwt.secret=" + VALID_SECRET,
                        "spring.datasource.url=jdbc:postgresql://db.projectref.supabase.co:5432/postgres",
                        "spring.datasource.username=postgres",
                        "spring.datasource.password=ValidSecureProdPassword#2026",
                        "spring.datasource.driver-class-name=org.postgresql.Driver",
                        "velorix.app.frontend-base-url=https://velorixsentinel.netlify.app",
                        "velorix.cors.allowed-origins=https://velorixsentinel.netlify.app",
                        "velorix.storage.provider=s3",
                        "velorix.storage.s3.bucket=velorix-captures-staging",
                        "velorix.storage.s3.region=us-east-1",
                        "spring.mail.host=smtp.sendgrid.net",
                        "spring.flyway.clean-disabled=true"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProductionProfileValidator.class);
                    ProductionProfileValidator validator = context.getBean(ProductionProfileValidator.class);
                    assertThat(validator.isProductionProfile()).isTrue();
                });
    }

    @Test
    @DisplayName("Context startup fails fast when JWT_SECRET is missing in production")
    void contextStartupFailsWhenJwtSecretMissingInProduction() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "velorix.security.jwt.secret=",
                        "spring.datasource.url=jdbc:postgresql://db.projectref.supabase.co:5432/postgres",
                        "spring.datasource.password=ValidSecureProdPassword#2026",
                        "velorix.app.frontend-base-url=https://velorixsentinel.netlify.app",
                        "velorix.cors.allowed-origins=https://velorixsentinel.netlify.app"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();
                    assertThat(failure.toString()).contains("ProductionConfigurationException");
                    assertThat(failure.toString()).contains("JWT_SECRET");
                });
    }

    @Test
    @DisplayName("Context startup fails fast when database URL points to localhost in production")
    void contextStartupFailsWhenDatabaseUrlPointsToLocalhostInProduction() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "velorix.security.jwt.secret=" + VALID_SECRET,
                        "spring.datasource.url=jdbc:postgresql://localhost:5432/velorix_sentinel",
                        "spring.datasource.password=ValidSecureProdPassword#2026",
                        "velorix.app.frontend-base-url=https://velorixsentinel.netlify.app",
                        "velorix.cors.allowed-origins=https://velorixsentinel.netlify.app"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();
                    assertThat(failure.toString()).contains("ProductionConfigurationException");
                    assertThat(failure.toString()).contains("spring.datasource.url");
                });
    }

    @Test
    @DisplayName("Context startup succeeds without production requirements when profile is test")
    void contextStartupSucceedsInNonProdProfile() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=test",
                        "spring.datasource.url=jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "velorix.security.jwt.secret="
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ProductionProfileValidator validator = context.getBean(ProductionProfileValidator.class);
                    assertThat(validator.isProductionProfile()).isFalse();
                });
    }
}
