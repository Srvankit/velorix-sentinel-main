package com.velorix.sentinel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velorix.sentinel.config.properties.StorageProperties;
import com.velorix.sentinel.dto.file.UploadedFileResponse;
import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.ActivityType;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.UploadStatus;
import com.velorix.sentinel.exception.ForbiddenException;
import com.velorix.sentinel.exception.ResourceConflictException;
import com.velorix.sentinel.mapper.UploadedFileMapper;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.security.CurrentUserProvider;
import com.velorix.sentinel.service.ActivityLogService;
import com.velorix.sentinel.service.UploadValidationService;
import com.velorix.sentinel.storage.FileStorageService;
import com.velorix.sentinel.storage.StoredObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class UploadedFileServiceImplTest {

    @Mock
    private UploadedFileRepository uploadedFileRepository;

    @Mock
    private UploadedFileMapper uploadedFileMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private UploadValidationService uploadValidationService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ActivityLogService activityLogService;

    @Mock
    private UserRepository userRepository;

    private StorageProperties storageProperties;
    private UploadedFileServiceImpl uploadedFileService;

    private User verifiedUser;
    private User unverifiedUser;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        storageProperties = new StorageProperties(
                "filesystem",
                "./data/captures",
                DataSize.ofMegabytes(100),
                null,
                null,
                null,
                10,
                DataSize.ofMegabytes(500));

        uploadedFileService = new UploadedFileServiceImpl(
                uploadedFileRepository,
                uploadedFileMapper,
                currentUserProvider,
                uploadValidationService,
                fileStorageService,
                activityLogService,
                userRepository,
                storageProperties);

        verifiedUser = User.builder()
                .id(userId)
                .email("analyst@velorix.io")
                .firstName("Ada")
                .lastName("Lovelace")
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build();

        unverifiedUser = User.builder()
                .id(userId)
                .email("unverified@velorix.io")
                .firstName("Alan")
                .lastName("Turing")
                .role(Role.USER)
                .verified(false)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Upload rejected with 403 Forbidden when user email is not verified")
    void uploadRejectedWhenEmailNotVerified() {
        when(currentUserProvider.requireUserId()).thenReturn(userId);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(unverifiedUser));

        MockMultipartFile file = new MockMultipartFile("file", "traffic.pcap", "application/vnd.tcpdump.pcap", new byte[64]);

        assertThatThrownBy(() -> uploadedFileService.upload(file))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Please verify your email address before uploading captures.")
                .satisfies(ex -> assertThat(((ForbiddenException) ex).getCode()).isEqualTo("EMAIL_NOT_VERIFIED"));

        verify(activityLogService).failure(
                eq(unverifiedUser),
                eq(ActivityType.FILE_UPLOAD_FAILED),
                eq("Capture upload rejected: Email verification is required before uploading captures."));

        verify(uploadValidationService, never()).validate(any());
        verify(fileStorageService, never()).store(anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("Upload rejected with 409 Conflict when user active capture count reaches quota")
    void uploadRejectedWhenFileCountQuotaExceeded() {
        when(currentUserProvider.requireUserId()).thenReturn(userId);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(verifiedUser));
        when(uploadValidationService.validate(any())).thenReturn("traffic.pcap");
        when(uploadedFileRepository.countByUserIdAndUploadStatusNot(userId, UploadStatus.DELETED)).thenReturn(10L);

        MockMultipartFile file = new MockMultipartFile("file", "traffic.pcap", "application/vnd.tcpdump.pcap", new byte[64]);

        assertThatThrownBy(() -> uploadedFileService.upload(file))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Capture file quota exceeded (max 10 files). Please delete older captures before uploading new files.");

        verify(activityLogService).failure(
                eq(verifiedUser),
                eq(ActivityType.FILE_UPLOAD_FAILED),
                eq("Capture file quota exceeded (max 10 files). Please delete older captures before uploading new files."));

        verify(fileStorageService, never()).store(anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("Upload rejected with 409 Conflict when storage quota (500 MB) would be exceeded")
    void uploadRejectedWhenStorageSizeQuotaExceeded() {
        when(currentUserProvider.requireUserId()).thenReturn(userId);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(verifiedUser));
        when(uploadValidationService.validate(any())).thenReturn("large.pcap");
        when(uploadedFileRepository.countByUserIdAndUploadStatusNot(userId, UploadStatus.DELETED)).thenReturn(3L);

        long maxBytes = storageProperties.maxStoragePerUserBytes(); // 500 MB
        long currentBytes = maxBytes - 100; // 100 bytes left
        when(uploadedFileRepository.sumBytesByUserIdAndUploadStatusNot(userId, UploadStatus.DELETED)).thenReturn(currentBytes);

        MockMultipartFile file = new MockMultipartFile("file", "large.pcap", "application/vnd.tcpdump.pcap", new byte[200]);

        assertThatThrownBy(() -> uploadedFileService.upload(file))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Storage quota exceeded (max 500 MB). Please delete older captures before uploading new files.");

        verify(activityLogService).failure(
                eq(verifiedUser),
                eq(ActivityType.FILE_UPLOAD_FAILED),
                eq("Storage quota exceeded (max 500 MB). Please delete older captures before uploading new files."));

        verify(fileStorageService, never()).store(anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("Upload rolls back stored binary if post-write size check exceeds quota")
    void uploadRollsBackWhenStoredSizeExceedsQuota() {
        when(currentUserProvider.requireUserId()).thenReturn(userId);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(verifiedUser));
        when(uploadValidationService.validate(any())).thenReturn("stream.pcap");
        when(uploadedFileRepository.countByUserIdAndUploadStatusNot(userId, UploadStatus.DELETED)).thenReturn(1L);

        long maxBytes = storageProperties.maxStoragePerUserBytes(); // 500 MB
        long currentBytes = maxBytes - 1000;
        when(uploadedFileRepository.sumBytesByUserIdAndUploadStatusNot(userId, UploadStatus.DELETED)).thenReturn(currentBytes);

        // Pre-check uses file.getSize() which says 100 bytes
        MockMultipartFile file = new MockMultipartFile("file", "stream.pcap", "application/vnd.tcpdump.pcap", new byte[100]);

        // Stored object actually consumed 2000 bytes (e.g. streaming disparity)
        StoredObject stored = new StoredObject("storage-key-1", 2000L, "hash123");
        when(fileStorageService.store(anyString(), any(InputStream.class), anyLong())).thenReturn(stored);

        assertThatThrownBy(() -> uploadedFileService.upload(file))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Storage quota exceeded (max 500 MB). Please delete older captures before uploading new files.");

        verify(fileStorageService).delete(anyString());
        verify(uploadedFileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Successful upload locks user row, verifies email and quotas, and persists file")
    void successfulUploadWithinQuotas() {
        when(currentUserProvider.requireUserId()).thenReturn(userId);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(verifiedUser));
        when(uploadValidationService.validate(any())).thenReturn("capture.pcap");
        when(uploadedFileRepository.countByUserIdAndUploadStatusNot(userId, UploadStatus.DELETED)).thenReturn(2L);
        when(uploadedFileRepository.sumBytesByUserIdAndUploadStatusNot(userId, UploadStatus.DELETED)).thenReturn(10_000L);

        MockMultipartFile file = new MockMultipartFile("file", "capture.pcap", "application/vnd.tcpdump.pcap", new byte[1000]);
        StoredObject stored = new StoredObject("storage-key-success", 1000L, "checksum-abc");
        when(fileStorageService.store(anyString(), any(InputStream.class), anyLong())).thenReturn(stored);
        when(uploadedFileRepository.findByUserIdAndChecksumAndUploadStatusNot(userId, "checksum-abc", UploadStatus.DELETED))
                .thenReturn(Optional.empty());

        UploadedFile savedEntity = UploadedFile.builder()
                .id(UUID.randomUUID())
                .user(verifiedUser)
                .originalFileName("capture.pcap")
                .storedFileName("storage-key-success")
                .fileSize(1000L)
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .checksum("checksum-abc")
                .build();
        when(uploadedFileRepository.save(any(UploadedFile.class))).thenReturn(savedEntity);

        UploadedFileResponse expectedResponse = new UploadedFileResponse(
                savedEntity.getId(),
                userId,
                "capture.pcap",
                "storage-key-success",
                1000L,
                "application/vnd.tcpdump.pcap",
                UploadStatus.READY_FOR_ANALYSIS,
                "checksum-abc",
                Instant.now(),
                Instant.now());
        when(uploadedFileMapper.toResponse(savedEntity)).thenReturn(expectedResponse);

        UploadedFileResponse response = uploadedFileService.upload(file);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(savedEntity.getId());
        verify(userRepository).findByIdForUpdate(userId);
        verify(activityLogService).success(eq(verifiedUser), eq(ActivityType.FILE_UPLOADED), anyString());
    }
}
