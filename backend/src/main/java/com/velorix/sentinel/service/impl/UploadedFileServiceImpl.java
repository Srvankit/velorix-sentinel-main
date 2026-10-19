package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.common.PageResponse;
import com.velorix.sentinel.dto.common.PageRequestParams;
import com.velorix.sentinel.dto.file.UploadedFileFilter;
import com.velorix.sentinel.dto.file.UploadedFileResponse;
import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.ActivityType;
import com.velorix.sentinel.entity.enums.UploadStatus;
import com.velorix.sentinel.exception.ResourceConflictException;
import com.velorix.sentinel.exception.ResourceNotFoundException;
import com.velorix.sentinel.mapper.UploadedFileMapper;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.repository.spec.UploadedFileSpecifications;
import com.velorix.sentinel.security.CurrentUserProvider;
import com.velorix.sentinel.service.ActivityLogService;
import com.velorix.sentinel.service.UploadValidationService;
import com.velorix.sentinel.service.UploadedFileService;
import com.velorix.sentinel.storage.FileStorageService;
import com.velorix.sentinel.storage.StoredObject;
import com.velorix.sentinel.util.FileNameUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Owner-scoped ingestion and reads over the {@code uploaded_files} table.
 *
 * <p>The binary and the metadata row are kept consistent: a failed database write removes
 * the freshly stored object again, and a delete only drops the binary once the row has
 * been marked {@link UploadStatus#DELETED}.</p>
 */
@Service
@Transactional(readOnly = true)
public class UploadedFileServiceImpl implements UploadedFileService {

    private static final Logger log = LoggerFactory.getLogger(UploadedFileServiceImpl.class);
    private static final String RESOURCE = "Uploaded file";

    private final UploadedFileRepository uploadedFileRepository;
    private final UploadedFileMapper uploadedFileMapper;
    private final CurrentUserProvider currentUserProvider;
    private final UploadValidationService uploadValidationService;
    private final FileStorageService fileStorageService;
    private final ActivityLogService activityLogService;

    public UploadedFileServiceImpl(
            UploadedFileRepository uploadedFileRepository,
            UploadedFileMapper uploadedFileMapper,
            CurrentUserProvider currentUserProvider,
            UploadValidationService uploadValidationService,
            FileStorageService fileStorageService,
            ActivityLogService activityLogService) {
        this.uploadedFileRepository = uploadedFileRepository;
        this.uploadedFileMapper = uploadedFileMapper;
        this.currentUserProvider = currentUserProvider;
        this.uploadValidationService = uploadValidationService;
        this.fileStorageService = fileStorageService;
        this.activityLogService = activityLogService;
    }

    @Override
    @Transactional
    public UploadedFileResponse upload(MultipartFile file) {
        User owner = currentUserProvider.requireUser();
        String originalName;
        try {
            originalName = uploadValidationService.validate(file);
        } catch (RuntimeException ex) {
            log.warn("Upload rejected for user {}: {}", owner.getId(), ex.getMessage());
            activityLogService.failure(owner, ActivityType.FILE_UPLOAD_FAILED,
                    "Capture upload rejected: " + ex.getMessage());
            throw ex;
        }

        String storageKey = FileNameUtils.uniqueStorageKey(FileNameUtils.extension(originalName));
        StoredObject stored = write(owner, originalName, storageKey, file);

        try {
            uploadedFileRepository
                    .findByUserIdAndChecksumAndUploadStatusNot(owner.getId(), stored.checksum(), UploadStatus.DELETED)
                    .ifPresent(existing -> {
                        throw new ResourceConflictException(
                                "This capture has already been uploaded as '%s'."
                                        .formatted(existing.getOriginalFileName()));
                    });

            UploadedFile saved = uploadedFileRepository.save(UploadedFile.builder()
                    .user(owner)
                    .originalFileName(originalName)
                    .storedFileName(stored.storageKey())
                    .fileSize(stored.sizeBytes())
                    .fileType(resolveContentType(file, originalName))
                    .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                    .checksum(stored.checksum())
                    .build());

            log.info("Capture '{}' ({} bytes) stored for user {} as {}",
                    originalName, stored.sizeBytes(), owner.getId(), saved.getId());
            activityLogService.success(owner, ActivityType.FILE_UPLOADED,
                    "Uploaded capture '%s' (%d bytes)".formatted(originalName, stored.sizeBytes()));
            return uploadedFileMapper.toResponse(saved);
        } catch (RuntimeException ex) {
            fileStorageService.delete(storageKey);
            log.warn("Rolled back stored capture {} for user {}: {}", storageKey, owner.getId(), ex.getMessage());
            activityLogService.failure(owner, ActivityType.FILE_UPLOAD_FAILED,
                    "Capture upload failed: " + ex.getMessage());
            throw ex;
        }
    }

    @Override
    public PageResponse<UploadedFileResponse> list(UploadedFileFilter filter, PageRequestParams pageParams) {
        UUID ownerScope = currentUserProvider.ownerScope();
        UploadStatus status = filter == null ? null : filter.status();
        String search = filter == null ? null : filter.normalisedSearch();
        Page<UploadedFile> page = uploadedFileRepository.findAll(
                UploadedFileSpecifications.visible(ownerScope, status, search),
                pageParams.toPageable());
        log.debug("Listed {} captures (ownerScope={})", page.getTotalElements(), ownerScope);
        return PageResponse.from(page, uploadedFileMapper::toResponse);
    }

    @Override
    public UploadedFileResponse getById(UUID fileId) {
        return uploadedFileMapper.toResponse(loadOwned(fileId));
    }

    @Override
    @Transactional
    public void delete(UUID fileId) {
        UploadedFile file = loadOwned(fileId);
        if (file.getUploadStatus().isDeleted()) {
            throw ResourceNotFoundException.of(RESOURCE, fileId);
        }

        file.setUploadStatus(UploadStatus.DELETED);
        uploadedFileRepository.save(file);
        fileStorageService.delete(file.getStoredFileName());

        log.info("Capture {} ('{}') deleted by user {}",
                file.getId(), file.getOriginalFileName(), currentUserProvider.requireUserId());
        activityLogService.success(currentUserProvider.requireUser(), ActivityType.FILE_DELETED,
                "Deleted capture '%s'".formatted(file.getOriginalFileName()));
    }

    @Override
    public long countForCurrentUser() {
        return uploadedFileRepository.countByUserIdAndUploadStatusNot(
                currentUserProvider.requireUserId(), UploadStatus.DELETED);
    }

    private StoredObject write(User owner, String originalName, String storageKey, MultipartFile file) {
        try (InputStream stream = file.getInputStream()) {
            return fileStorageService.store(storageKey, stream, uploadValidationService.maxFileSizeBytes());
        } catch (IOException ex) {
            log.error("Capture '{}' from user {} could not be read", originalName, owner.getId(), ex);
            activityLogService.failure(owner, ActivityType.FILE_UPLOAD_FAILED,
                    "Capture upload failed while reading '%s'".formatted(originalName));
            throw new com.velorix.sentinel.exception.StorageException(
                    "The upload could not be read. Please try again.", ex);
        } catch (RuntimeException ex) {
            fileStorageService.delete(storageKey);
            activityLogService.failure(owner, ActivityType.FILE_UPLOAD_FAILED,
                    "Capture upload failed for '%s': %s".formatted(originalName, ex.getMessage()));
            throw ex;
        }
    }

    private String resolveContentType(MultipartFile file, String originalName) {
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        return "pcapng".equals(FileNameUtils.extension(originalName))
                ? "application/x-pcapng" : "application/vnd.tcpdump.pcap";
    }

    private UploadedFile loadOwned(UUID fileId) {
        UUID ownerScope = currentUserProvider.ownerScope();
        UploadedFile file = (ownerScope == null
                ? uploadedFileRepository.findById(fileId)
                : uploadedFileRepository.findByIdAndUserId(fileId, ownerScope))
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, fileId));
        if (file.getUploadStatus().isDeleted()) {
            throw ResourceNotFoundException.of(RESOURCE, fileId);
        }
        return file;
    }
}
