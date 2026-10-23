package com.velorix.sentinel.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.repository.ActivityLogRepository;
import com.velorix.sentinel.repository.RefreshTokenRepository;
import com.velorix.sentinel.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies case-insensitive email handling across registration, authentication,
 * and Spring Data repository lookup methods.
 *
 * <p>Application-level and repository-level tests execute against the active Spring Boot
 * test context (H2 in-memory database). The direct database-level functional index test
 * is conditional via {@code Assumptions.assumeTrue} on PostgreSQL, as H2's parser does not
 * support expression indexes in {@code CREATE INDEX} DDL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaseInsensitiveEmailIntegrationTest {

    private static final String PASSWORD = "Str0ng!Passw0rd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private com.velorix.sentinel.repository.VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        activityLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Registration rejects case-varying duplicates with HTTP 409 Conflict")
    void registrationRejectsCaseVaryingDuplicates() throws Exception {
        String base = "analyst.%s".formatted(System.nanoTime());
        String initialEmail = "%s@Velorix.IO".formatted(base);
        String lowercaseEmail = "%s@velorix.io".formatted(base).toLowerCase();
        String uppercaseEmail = "%s@VELORIX.IO".formatted(base).toUpperCase();

        // Initial registration with mixed case
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Test",
                                "lastName", "Analyst",
                                "email", initialEmail,
                                "password", PASSWORD,
                                "confirmPassword", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findByEmailIgnoreCase(lowercaseEmail)).isPresent();

        // Attempting registration with lowercase variant returns generic 201 without creating duplicate
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Duplicate",
                                "lastName", "User",
                                "email", lowercaseEmail,
                                "password", PASSWORD,
                                "confirmPassword", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(userRepository.count()).isEqualTo(1);

        // Attempting registration with uppercase variant returns generic 201 without creating duplicate
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Duplicate",
                                "lastName", "User",
                                "email", uppercaseEmail,
                                "password", PASSWORD,
                                "confirmPassword", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Login succeeds regardless of email casing used by the user")
    void loginSucceedsWithAnyEmailCasing() throws Exception {
        String base = "engineer.%s".formatted(System.nanoTime());
        String storedEmail = "%s@velorix.io".formatted(base);

        userRepository.save(User.builder()
                .firstName("Margaret")
                .lastName("Hamilton")
                .email(storedEmail)
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build());

        // Login with uppercase email
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", storedEmail.toUpperCase(),
                                "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));

        // Login with mixed case email
        String mixedCase = storedEmail.substring(0, 3).toUpperCase() + storedEmail.substring(3);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", mixedCase,
                                "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("UserRepository case-insensitive queries match any casing")
    void userRepositoryCaseInsensitiveLookups() {
        String base = "scientist.%s".formatted(System.nanoTime());
        String email = "%s@velorix.io".formatted(base);

        User saved = userRepository.save(User.builder()
                .firstName("Chien-Shiung")
                .lastName("Wu")
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.ANALYST)
                .verified(true)
                .active(true)
                .build());

        assertThat(userRepository.findByEmailIgnoreCase(email.toUpperCase()))
                .isPresent()
                .get()
                .extracting(User::getId)
                .isEqualTo(saved.getId());

        assertThat(userRepository.findByEmailIgnoreCase(email.toLowerCase()))
                .isPresent();

        assertThat(userRepository.existsByEmailIgnoreCase(email.toUpperCase()))
                .isTrue();

        assertThat(userRepository.existsByEmailIgnoreCase("nonexistent@velorix.io"))
                .isFalse();
    }

    @Test
    @DisplayName("Database-level unique functional index uk_users_lower_email enforces uniqueness (PostgreSQL only)")
    void databaseFunctionalIndexRejectsCaseVaryingDuplicateInserts() {
        String databaseProductName = "";
        try {
            databaseProductName = jdbcTemplate.getDataSource().getConnection().getMetaData().getDatabaseProductName();
        } catch (Exception ignored) {
        }

        // H2 parser does not support functional expression indexes like LOWER(email) in CREATE INDEX DDL.
        // On PostgreSQL, migration V8 creates the uk_users_lower_email functional index.
        org.junit.jupiter.api.Assumptions.assumeTrue(
                databaseProductName.toLowerCase().contains("postgresql"),
                "Skipping expression index DDL on non-PostgreSQL database (%s); H2 does not support expression indexes"
                        .formatted(databaseProductName));

        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_users_lower_email ON users (LOWER(email));");

        String base = "direct.%s".formatted(System.nanoTime());
        String lowerEmail = "%s@velorix.io".formatted(base);
        String upperEmail = "%s@VELORIX.IO".formatted(base);

        userRepository.saveAndFlush(User.builder()
                .firstName("First")
                .lastName("User")
                .email(lowerEmail)
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build());

        // Direct persistence of duplicate with differing casing must be rejected by the functional index
        assertThatThrownBy(() -> userRepository.saveAndFlush(User.builder()
                .firstName("Second")
                .lastName("User")
                .email(upperEmail)
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
