package com.velorix.sentinel.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task 6.3 — Production Exception Handler Gap Remediation (M6).
 *
 * <p>Verifies that common HTTP protocol and client exceptions return appropriate
 * 4xx statuses with clean JSON error envelopes instead of unhandled 500 errors:
 * <ul>
 *   <li>HttpRequestMethodNotSupportedException → 405 Method Not Allowed</li>
 *   <li>HttpMediaTypeNotSupportedException → 415 Unsupported Media Type</li>
 *   <li>MissingServletRequestPartException → 400 Bad Request</li>
 *   <li>MethodArgumentTypeMismatchException → 400 Bad Request</li>
 * </ul>
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Task 6.3 — Global Exception Handler Integration Tests (M6)")
class GlobalExceptionHandlerIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();
        activityLogRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(User.builder()
                .firstName("Test")
                .lastName("User")
                .email("user.%s@velorix.io".formatted(System.nanoTime()))
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build());

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        userToken = objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("PUT /api/v1/auth/login returns 405 Method Not Allowed with JSON envelope and Allow header")
    void unsupportedMethodOnLoginReturns405() throws Exception {
        mockMvc.perform(put("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@velorix.io\",\"password\":\"password\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists(HttpHeaders.ALLOW))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.errors[0].code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    @DisplayName("PUT /api/auth/login returns 405 Method Not Allowed")
    void unsupportedMethodOnAuthBaseLoginReturns405() throws Exception {
        mockMvc.perform(put("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@velorix.io\",\"password\":\"password\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.errors[0].code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("PATCH /api/auth/register returns 405 Method Not Allowed")
    void unsupportedMethodOnRegisterReturns405() throws Exception {
        mockMvc.perform(patch("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.errors[0].code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with text/plain returns 415 Unsupported Media Type")
    void unsupportedContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text payload"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().exists(HttpHeaders.ACCEPT))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.errors[0].code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/files/upload with missing 'file' part returns 400 Bad Request")
    void missingRequestPartReturns400() throws Exception {
        // Send a multipart request with part named 'wrong_part_name' instead of 'file'
        MockMultipartFile wrongPart = new MockMultipartFile(
                "wrong_part_name",
                "test.pcap",
                "application/vnd.tcpdump.pcap",
                new byte[]{0x01, 0x02});

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(wrongPart)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].code").value("MISSING_REQUEST_PART"))
                .andExpect(jsonPath("$.errors[0].field").value("file"));
    }

    @Test
    @DisplayName("GET /api/v1/files/not-a-valid-uuid returns 400 Bad Request (Type Mismatch)")
    void invalidUuidPathVariableReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/files/not-a-valid-uuid")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].code").value("TYPE_MISMATCH"));
    }
}

