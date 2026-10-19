package com.velorix.sentinel.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.UploadStatus;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.storage.FileStorageService;
import java.nio.ByteBuffer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end coverage of the capture ingestion API: validation, storage, duplicate
 * detection, ownership and deletion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileUploadIntegrationTest {

    private static final String PASSWORD = "Str0ng!Passw0rd";
    private static final int PCAP_MAGIC = 0xa1b2c3d4;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/v1/files/upload").file(capture("a.pcap", 64)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadsStoresAndDeletesACapture() throws Exception {
        User owner = seedUser("owner");
        String token = login(owner.getEmail());

        String body = mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(capture("session-01.pcap", 512))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalFileName").value("session-01.pcap"))
                .andExpect(jsonPath("$.data.uploadStatus").value("READY_FOR_ANALYSIS"))
                .andExpect(jsonPath("$.data.fileSize").value(512))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        String id = data.path("id").asText();
        String storedName = data.path("storedFileName").asText();

        assertThat(storedName).doesNotContain("session-01").endsWith(".pcap");
        assertThat(fileStorageService.exists(storedName)).isTrue();

        mockMvc.perform(get("/api/v1/files").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(delete("/api/v1/files/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(fileStorageService.exists(storedName)).isFalse();
        assertThat(uploadedFileRepository.findById(java.util.UUID.fromString(id)))
                .get()
                .extracting(f -> f.getUploadStatus())
                .isEqualTo(UploadStatus.DELETED);

        mockMvc.perform(get("/api/v1/files/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsCapturesWithNullEmptyAndCaseInsensitiveSearch() throws Exception {
        User owner = seedUser("searcher");
        String token = login(owner.getEmail());
        String auth = "Bearer " + token;

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(capture("Morning-Capture.pcap", 256))
                        .header("Authorization", auth))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(capture("evening.pcapng", 320))
                        .header("Authorization", auth))
                .andExpect(status().isCreated());

        // no search parameter at all
        mockMvc.perform(get("/api/v1/files").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // exact production request: pagination and sorting must not introduce a search bind
        mockMvc.perform(get("/api/v1/files")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "createdAt")
                        .param("direction", "DESC")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].originalFileName").value("evening.pcapng"));

        // empty search behaves like "any"
        mockMvc.perform(get("/api/v1/files").param("search", "").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // case-insensitive partial match
        mockMvc.perform(get("/api/v1/files").param("search", "MORNING").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].originalFileName").value("Morning-Capture.pcap"));

        mockMvc.perform(get("/api/v1/files").param("search", "eVeNiNg").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/files").param("search", "nomatch").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // filtering composes with case-insensitive search and pagination
        mockMvc.perform(get("/api/v1/files")
                        .param("status", "READY_FOR_ANALYSIS")
                        .param("search", "CAPTURE")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "originalFileName")
                        .param("direction", "ASC")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].originalFileName").value("Morning-Capture.pcap"));
    }



    @Test
    void rejectsUnsupportedExtensionEmptyAndCorruptedFiles() throws Exception {
        String token = login(seedUser("validator").getEmail());

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1, 2, 3, 4}))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "empty.pcap", "application/octet-stream", new byte[0]))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "corrupt.pcap", "application/octet-stream",
                                "not a capture at all".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateCapturesByChecksum() throws Exception {
        String token = login(seedUser("dupe").getEmail());

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(capture("first.pcap", 256))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(capture("second.pcap", 256))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void neverExposesAnotherAccountsCapture() throws Exception {
        String ownerToken = login(seedUser("alice").getEmail());
        String intruderToken = login(seedUser("mallory").getEmail());

        String body = mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(capture("private.pcapng", 320))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).path("data").path("id").asText();

        mockMvc.perform(get("/api/v1/files/" + id).header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/files/" + id).header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/files").header("Authorization", "Bearer " + intruderToken))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    /** A byte-identical, well formed libpcap payload of the requested length. */
    private MockMultipartFile capture(String name, int size) {
        byte[] bytes = new byte[size];
        ByteBuffer.wrap(bytes).putInt(PCAP_MAGIC);
        for (int i = 4; i < size; i++) {
            bytes[i] = (byte) (i % 251);
        }
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    private User seedUser(String prefix) {
        return userRepository.save(User.builder()
                .firstName("Test")
                .lastName("Analyst")
                .email("%s.%s@velorix.io".formatted(prefix, System.nanoTime()))
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build());
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }
}
