package com.velorix.sentinel;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base integration test harness for Velorix Sentinel.
 *
 * <p>Spins up a shared singleton PostgreSQL 16 container via Testcontainers,
 * ensuring all integration tests run against a real PostgreSQL database with
 * Flyway migrations V1–V12 applied and Hibernate schema validation enforced
 * (Finding H9, P1-1).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    public static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER;

    static {
        POSTGRESQL_CONTAINER = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("sentinel_test")
                .withUsername("sentinel")
                .withPassword("sentinel");
        POSTGRESQL_CONTAINER.start();
    }

    @DynamicPropertySource
    static void configurePostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
