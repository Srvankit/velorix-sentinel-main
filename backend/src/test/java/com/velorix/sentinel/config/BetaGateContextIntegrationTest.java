package com.velorix.sentinel.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.config.properties.BetaProperties;
import com.velorix.sentinel.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Task 8.4 — Spring Application Context Boot Test with Closed Beta Gate.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Spring container starts cleanly when {@code velorix.beta.invite-only=false} (Gate OFF).</li>
 *   <li>Spring container starts cleanly when {@code velorix.beta.invite-only=true} (Gate ON) with allowed entries.</li>
 *   <li>{@link BetaProperties} is correctly bound via {@code @EnableConfigurationProperties}.</li>
 *   <li>{@link AuthService} and {@link ProductionProfileValidator} constructors are wired unambiguously with {@code @Autowired}.</li>
 * </ul>
 * </p>
 *
 * <p><strong>ENVIRONMENT NOTE:</strong> Uses the {@code test} profile which requires PostgreSQL via Testcontainers.
 * When local Docker daemon is unavailable, this test is marked <em>UNVERIFIED locally - needs Docker/CI</em>
 * and must be validated in the CI pipeline container.</p>
 */
@DisplayName("Task 8.4 — Spring Context Boot with Beta Gate ON/OFF (UNVERIFIED locally - needs Docker/CI)")
class BetaGateContextIntegrationTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
            "velorix.beta.invite-only=false",
            "velorix.beta.allowed-emails="
    })
    @DisplayName("Spring Context with Beta Gate OFF")
    class GateOffContextTest {

        @Autowired
        private BetaProperties betaProperties;

        @Autowired
        private AuthService authService;

        @Autowired
        private ProductionProfileValidator profileValidator;

        @Test
        @DisplayName("Context boots cleanly and injects BetaProperties with inviteOnly=false")
        void contextBootsWithBetaGateOff() {
            assertThat(betaProperties).isNotNull();
            assertThat(betaProperties.inviteOnly()).isFalse();
            assertThat(authService).isNotNull();
            assertThat(profileValidator).isNotNull();
            assertThat(betaProperties.isAllowed("any-unlisted-user@example.com")).isTrue();
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
            "velorix.beta.invite-only=true",
            "velorix.beta.allowed-emails=tester@example.com, @domain.org"
    })
    @DisplayName("Spring Context with Beta Gate ON")
    class GateOnContextTest {

        @Autowired
        private BetaProperties betaProperties;

        @Autowired
        private AuthService authService;

        @Autowired
        private ProductionProfileValidator profileValidator;

        @Test
        @DisplayName("Context boots cleanly and injects BetaProperties with inviteOnly=true and parsed allowlist")
        void contextBootsWithBetaGateOn() {
            assertThat(betaProperties).isNotNull();
            assertThat(betaProperties.inviteOnly()).isTrue();
            assertThat(betaProperties.allowedEntriesCount()).isEqualTo(2);
            assertThat(authService).isNotNull();
            assertThat(profileValidator).isNotNull();
            assertThat(betaProperties.isAllowed("tester@example.com")).isTrue();
            assertThat(betaProperties.isAllowed("colleague@domain.org")).isTrue();
            assertThat(betaProperties.isAllowed("uninvited@example.com")).isFalse();
        }
    }
}
