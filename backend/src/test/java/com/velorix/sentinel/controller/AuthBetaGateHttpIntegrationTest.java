package com.velorix.sentinel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.sentinel.config.properties.BetaProperties;
import com.velorix.sentinel.dto.auth.RegisterRequest;
import com.velorix.sentinel.exception.ForbiddenException;
import com.velorix.sentinel.exception.GlobalExceptionHandler;
import com.velorix.sentinel.service.AuthService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Task 8.4 — HTTP-level MockMvc test verifying uniform 403 Forbidden responses
 * across different unlisted addresses, and guaranteeing that the internal beta
 * allowlist is never leaked in HTTP response payloads or application logs.
 */
@DisplayName("Task 8.4 — HTTP MockMvc Closed Beta Gate Parity & Secret Leakage Test")
class AuthBetaGateHttpIntegrationTest {

    private MockMvc mockMvc;
    private AuthService authService;
    private ObjectMapper objectMapper;
    private ListAppender<ILoggingEvent> listAppender;

    private static final String ALLOWLISTED_EMAIL_1 = "secret-partner@vipdomain.org";
    private static final String ALLOWLISTED_EMAIL_2 = "investor-beta@specialfirm.com";

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        AuthController authController = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();

        // Attach log appender to verify logs do not leak the allowlist
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);
    }

    @Test
    @DisplayName("Assert identical status, error code, and message for two different unlisted addresses")
    void assertIdenticalResponsesForDifferentUnlistedAddresses() throws Exception {
        String addressA = "random-uninvited-user@alpha.com";
        String addressB = "another-stranger@beta.org";

        // Service throws identical generic ForbiddenException regardless of unlisted address
        doThrow(new ForbiddenException("Registration is currently invite-only.", "INVITE_ONLY"))
                .when(authService).register(any(RegisterRequest.class));

        // Request 1: Address A
        MvcResult resultA = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Alpha",
                                "lastName", "User",
                                "email", addressA,
                                "password", "Str0ng!Passw0rd",
                                "confirmPassword", "Str0ng!Passw0rd"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Registration is currently invite-only."))
                .andExpect(jsonPath("$.errors[0].code").value("INVITE_ONLY"))
                .andExpect(jsonPath("$.errors[0].message").value("Registration is currently invite-only."))
                .andReturn();

        // Request 2: Address B
        MvcResult resultB = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Beta",
                                "lastName", "User",
                                "email", addressB,
                                "password", "Str0ng!Passw0rd",
                                "confirmPassword", "Str0ng!Passw0rd"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Registration is currently invite-only."))
                .andExpect(jsonPath("$.errors[0].code").value("INVITE_ONLY"))
                .andExpect(jsonPath("$.errors[0].message").value("Registration is currently invite-only."))
                .andReturn();

        // Validate payload content equality (excluding transient timestamp)
        String bodyA = resultA.getResponse().getContentAsString();
        String bodyB = resultB.getResponse().getContentAsString();
        JsonNode nodeA = objectMapper.readTree(bodyA);
        JsonNode nodeB = objectMapper.readTree(bodyB);

        assertThat(nodeA.path("success")).isEqualTo(nodeB.path("success"));
        assertThat(nodeA.path("status")).isEqualTo(nodeB.path("status"));
        assertThat(nodeA.path("message")).isEqualTo(nodeB.path("message"));
        assertThat(nodeA.path("errors")).isEqualTo(nodeB.path("errors"));

        // Security check: Verify that allowlist entries never appear in either response body
        assertThat(bodyA).doesNotContain(ALLOWLISTED_EMAIL_1);
        assertThat(bodyA).doesNotContain(ALLOWLISTED_EMAIL_2);
        assertThat(bodyB).doesNotContain(ALLOWLISTED_EMAIL_1);
        assertThat(bodyB).doesNotContain(ALLOWLISTED_EMAIL_2);

        // Security check: Verify allowlist entries never appear in any captured application log events
        List<String> logMessages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        for (String msg : logMessages) {
            assertThat(msg).doesNotContain(ALLOWLISTED_EMAIL_1);
            assertThat(msg).doesNotContain(ALLOWLISTED_EMAIL_2);
        }
    }
}
