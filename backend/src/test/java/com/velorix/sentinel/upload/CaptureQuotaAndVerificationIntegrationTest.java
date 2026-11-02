package com.velorix.sentinel.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.UploadStatus;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.security.JwtTokenProvider;
import com.velorix.sentinel.storage.FileStorageService;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration coverage for Task 4.3:
 * - Email verification guard (unverified users blocked with 403 Forbidden)
 * - Capture file count quota (default 10 files max per user, 409 Conflict)
 * - Capture storage byte quota (default 500 MB max per user, 409 Conflict)
 * - Immediate quota release on soft-deletion
 * - Concurrent upload serialization via user row lock
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaptureQuotaAndVerificationIntegrationTest {

    private static final String PASSWORD = "Test!Password123";
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

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("Unverified user upload is rejected with 403 Forbidden and EMAIL_NOT_VERIFIED")
    void unverifiedUserUploadRejected() throws Exception {
        User unverified = seedUser("unverified-analyst", false);
        String token = jwtTokenProvider.issueAccessToken(
                unverified.getId(), unverified.getEmail(), unverified.getRole().authority()).token();

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(capture("traffic.pcap", 128, 1))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Please verify your email address before uploading captures."))
                .andExpect(jsonPath("$.errors[0].code").value("EMAIL_NOT_VERIFIED"));

        assertThat(uploadedFileRepository.countByUserIdAndUploadStatusNot(unverified.getId(), UploadStatus.DELETED))
                .isZero();
    }

    @Test
    @DisplayName("Verified user can upload up to 10 captures; 11th is rejected with 409 Conflict")
    void fileCountQuotaEnforcedAndReleasedOnDelete() throws Exception {
        User user = seedUser("quota-analyst", true);
        String token = jwtTokenProvider.issueAccessToken(
                user.getId(), user.getEmail(), user.getRole().authority()).token();

        List<UUID> uploadedIds = new ArrayList<>();

        // Upload 10 distinct files
        for (int i = 1; i <= 10; i++) {
            String body = mockMvc.perform(multipart("/api/v1/files/upload")
                            .file(capture("capture-" + i + ".pcap", 256, i))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andReturn().getResponse().getContentAsString();

            String id = objectMapper.readTree(body).path("data").path("id").asText();
            uploadedIds.add(UUID.fromString(id));
        }

        assertThat(uploadedFileRepository.countByUserIdAndUploadStatusNot(user.getId(), UploadStatus.DELETED))
                .isEqualTo(10L);

        // 11th upload must be rejected with 409 Conflict
        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(capture("capture-11.pcap", 256, 11))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Capture file quota exceeded (max 10 files). Please delete older captures before uploading new files."));

        assertThat(uploadedFileRepository.countByUserIdAndUploadStatusNot(user.getId(), UploadStatus.DELETED))
                .isEqualTo(10L);

        // Soft-delete 1 capture
        UUID toDelete = uploadedIds.get(0);
        mockMvc.perform(delete("/api/v1/files/" + toDelete)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(uploadedFileRepository.countByUserIdAndUploadStatusNot(user.getId(), UploadStatus.DELETED))
                .isEqualTo(9L);

        // Uploading now succeeds again
        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(capture("capture-replacement.pcap", 256, 99))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(uploadedFileRepository.countByUserIdAndUploadStatusNot(user.getId(), UploadStatus.DELETED))
                .isEqualTo(10L);
    }

    @Test
    @DisplayName("Storage quota of 500 MB is enforced with 409 Conflict")
    void storageSizeQuotaEnforced() throws Exception {
        User user = seedUser("storage-analyst", true);
        String token = jwtTokenProvider.issueAccessToken(
                user.getId(), user.getEmail(), user.getRole().authority()).token();

        // Seed an existing file that consumes nearly the full 500 MB quota (500 MB - 100 bytes)
        long maxBytes = 500L * 1024L * 1024L;
        long existingSize = maxBytes - 100L;

        UploadedFile largeFile = UploadedFile.builder()
                .user(user)
                .originalFileName("existing-large.pcap")
                .storedFileName("storage-large-" + UUID.randomUUID() + ".pcap")
                .fileSize(existingSize)
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .checksum("checksum-" + UUID.randomUUID())
                .build();
        uploadedFileRepository.save(largeFile);

        // Attempt to upload a 256 byte capture (existingSize + 256 > 500 MB)
        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(capture("overflow.pcap", 256, 1))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Storage quota exceeded (max 500 MB). Please delete older captures before uploading new files."));

        // Delete the large file
        mockMvc.perform(delete("/api/v1/files/" + largeFile.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Now the 256 byte capture succeeds
        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(capture("overflow.pcap", 256, 1))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Concurrent uploads from the same user are safely serialized via row lock")
    void concurrentUploadsSameUserSerialized() throws Exception {
        User user = seedUser("concurrency-analyst", true);
        String token = jwtTokenProvider.issueAccessToken(
                user.getId(), user.getEmail(), user.getRole().authority()).token();

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            tasks.add(() -> {
                try {
                    return mockMvc.perform(multipart("/api/v1/files/upload")
                                    .file(capture("concurrent-" + index + ".pcap", 512, index + 10))
                                    .header("Authorization", "Bearer " + token))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                } catch (Exception ex) {
                    return 500;
                }
            });
        }

        List<Future<Integer>> results = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Integer> result : results) {
            assertThat(result.get()).isEqualTo(201);
        }

        assertThat(uploadedFileRepository.countByUserIdAndUploadStatusNot(user.getId(), UploadStatus.DELETED))
                .isEqualTo(threadCount);
    }

    private User seedUser(String prefix, boolean verified) {
        return userRepository.save(User.builder()
                .firstName("Test")
                .lastName("Analyst")
                .email("%s.%s@velorix.io".formatted(prefix, System.nanoTime()))
                .password(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .verified(verified)
                .active(true)
                .build());
    }

    private MockMultipartFile capture(String name, int size, int seed) {
        byte[] bytes = new byte[size];
        ByteBuffer.wrap(bytes).putInt(PCAP_MAGIC);
        for (int i = 4; i < size; i++) {
            bytes[i] = (byte) ((i + seed * 31) % 251);
        }
        return new MockMultipartFile("file", name, "application/vnd.tcpdump.pcap", bytes);
    }
}

