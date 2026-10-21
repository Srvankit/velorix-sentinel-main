package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.config.properties.RetentionProperties;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.repository.ActivityLogRepository;
import com.velorix.sentinel.repository.PacketRepository;
import com.velorix.sentinel.repository.RefreshTokenRepository;
import com.velorix.sentinel.repository.ThreatRepository;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.repository.VerificationTokenRepository;
import com.velorix.sentinel.service.DatabaseRetentionService;
import com.velorix.sentinel.storage.FileStorageService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scheduled database retention and lifecycle purging implementation.
 *
 * <p>Executes off-peak cleanup of expired tokens, stale audit trails, and soft-deleted files
 * using bounded batch transactions to minimize row locking and write amplification.</p>
 */
@Service
@Slf4j
public class DatabaseRetentionServiceImpl implements DatabaseRetentionService {

    /** Maximum batches to process in a single invocation to prevent infinite loops. */
    private static final int MAX_BATCH_ITERATIONS = 100;

    private final RetentionProperties retentionProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final PacketRepository packetRepository;
    private final ThreatRepository threatRepository;
    private final com.velorix.sentinel.repository.AnalysisSummaryRepository analysisSummaryRepository;
    private final FileStorageService fileStorageService;
    private final TransactionTemplate transactionTemplate;

    public DatabaseRetentionServiceImpl(
            RetentionProperties retentionProperties,
            RefreshTokenRepository refreshTokenRepository,
            VerificationTokenRepository verificationTokenRepository,
            ActivityLogRepository activityLogRepository,
            UploadedFileRepository uploadedFileRepository,
            PacketRepository packetRepository,
            ThreatRepository threatRepository,
            com.velorix.sentinel.repository.AnalysisSummaryRepository analysisSummaryRepository,
            FileStorageService fileStorageService,
            PlatformTransactionManager transactionManager) {
        this.retentionProperties = retentionProperties;
        this.refreshTokenRepository = refreshTokenRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.activityLogRepository = activityLogRepository;
        this.uploadedFileRepository = uploadedFileRepository;
        this.packetRepository = packetRepository;
        this.threatRepository = threatRepository;
        this.analysisSummaryRepository = analysisSummaryRepository;
        this.fileStorageService = fileStorageService;

        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(cron = "${velorix.retention.cron:0 0 3 * * *}", zone = "${velorix.retention.zone:UTC}")
    @Override
    public RetentionReport runRetentionCleanup() {
        if (!retentionProperties.enabled()) {
            log.debug("Database retention cleanup skipped: disabled by configuration");
            return RetentionReport.success(0, 0, 0, 0);
        }

        long startMs = System.currentTimeMillis();
        log.info("Starting scheduled database retention cleanup (batchSize={})", retentionProperties.batchSize());

        Instant now = Instant.now();
        int batchSize = retentionProperties.batchSize();
        List<String> errors = new ArrayList<>();

        int refreshTokensPurged = 0;
        try {
            Instant cutoff = now.minus(retentionProperties.refreshTokenRetention());
            refreshTokensPurged = purgeExpiredRefreshTokens(cutoff, batchSize);
        } catch (Exception ex) {
            log.error("Retention cleanup failed for refresh tokens: {}", ex.getMessage());
            errors.add("Refresh tokens: " + ex.getMessage());
        }

        int verificationTokensPurged = 0;
        try {
            Instant cutoff = now.minus(retentionProperties.verificationTokenRetention());
            verificationTokensPurged = purgeExpiredVerificationTokens(cutoff, batchSize);
        } catch (Exception ex) {
            log.error("Retention cleanup failed for verification tokens: {}", ex.getMessage());
            errors.add("Verification tokens: " + ex.getMessage());
        }

        int activityLogsPurged = 0;
        try {
            Instant cutoff = now.minus(retentionProperties.activityLogRetention());
            activityLogsPurged = purgeStaleActivityLogs(cutoff, batchSize);
        } catch (Exception ex) {
            log.error("Retention cleanup failed for activity logs: {}", ex.getMessage());
            errors.add("Activity logs: " + ex.getMessage());
        }

        int softDeletedFilesPurged = 0;
        try {
            Instant cutoff = now.minus(retentionProperties.softDeletedFileRetention());
            softDeletedFilesPurged = purgeSoftDeletedFiles(cutoff, batchSize);
        } catch (Exception ex) {
            log.error("Retention cleanup failed for soft-deleted files: {}", ex.getMessage());
            errors.add("Soft-deleted files: " + ex.getMessage());
        }

        long durationMs = System.currentTimeMillis() - startMs;
        if (errors.isEmpty()) {
            log.info("Database retention cleanup completed successfully in {} ms: "
                            + "{} refresh tokens, {} verification tokens, {} activity logs, {} files purged",
                    durationMs, refreshTokensPurged, verificationTokensPurged, activityLogsPurged, softDeletedFilesPurged);
            return RetentionReport.success(
                    refreshTokensPurged, verificationTokensPurged, activityLogsPurged, softDeletedFilesPurged);
        } else {
            String errorSummary = String.join("; ", errors);
            log.warn("Database retention cleanup completed with errors in {} ms: {}", durationMs, errorSummary);
            return RetentionReport.partialFailure(
                    refreshTokensPurged, verificationTokensPurged, activityLogsPurged, softDeletedFilesPurged, errorSummary);
        }
    }

    @Override
    public int purgeExpiredRefreshTokens(Instant cutoff, int batchSize) {
        int total = 0;
        for (int i = 0; i < MAX_BATCH_ITERATIONS; i++) {
            List<UUID> ids = transactionTemplate.execute(status ->
                    refreshTokenRepository.findExpiredIdsBefore(cutoff, PageRequest.of(0, batchSize)));

            if (ids == null || ids.isEmpty()) {
                break;
            }

            Integer deleted = transactionTemplate.execute(status ->
                    refreshTokenRepository.deleteByIdsIn(ids));

            int count = deleted == null ? 0 : deleted;
            total += count;

            if (ids.size() < batchSize) {
                break;
            }
        }
        return total;
    }

    @Override
    public int purgeExpiredVerificationTokens(Instant cutoff, int batchSize) {
        int total = 0;
        for (int i = 0; i < MAX_BATCH_ITERATIONS; i++) {
            List<UUID> ids = transactionTemplate.execute(status ->
                    verificationTokenRepository.findExpiredIdsBefore(cutoff, PageRequest.of(0, batchSize)));

            if (ids == null || ids.isEmpty()) {
                break;
            }

            Integer deleted = transactionTemplate.execute(status ->
                    verificationTokenRepository.deleteByIdsIn(ids));

            int count = deleted == null ? 0 : deleted;
            total += count;

            if (ids.size() < batchSize) {
                break;
            }
        }
        return total;
    }

    @Override
    public int purgeStaleActivityLogs(Instant cutoff, int batchSize) {
        int total = 0;
        for (int i = 0; i < MAX_BATCH_ITERATIONS; i++) {
            List<UUID> ids = transactionTemplate.execute(status ->
                    activityLogRepository.findIdsCreatedBefore(cutoff, PageRequest.of(0, batchSize)));

            if (ids == null || ids.isEmpty()) {
                break;
            }

            Integer deleted = transactionTemplate.execute(status ->
                    activityLogRepository.deleteByIdsIn(ids));

            int count = deleted == null ? 0 : deleted;
            total += count;

            if (ids.size() < batchSize) {
                break;
            }
        }
        return total;
    }

    @Override
    public int purgeSoftDeletedFiles(Instant cutoff, int batchSize) {
        int total = 0;
        for (int i = 0; i < MAX_BATCH_ITERATIONS; i++) {
            List<UUID> ids = transactionTemplate.execute(status ->
                    uploadedFileRepository.findSoftDeletedIdsBefore(cutoff, PageRequest.of(0, batchSize)));

            if (ids == null || ids.isEmpty()) {
                break;
            }

            for (UUID fileId : ids) {
                Boolean removed = transactionTemplate.execute(status -> {
                    UploadedFile file = uploadedFileRepository.findById(fileId).orElse(null);
                    if (file == null) {
                        return false;
                    }

                    // Strict guard: ensure file is truly soft-deleted and older than cutoff
                    if (!file.getUploadStatus().isDeleted() || !file.getUpdatedAt().isBefore(cutoff)) {
                        return false;
                    }

                    // Best-effort removal of the storage binary
                    try {
                        fileStorageService.delete(file.getStoredFileName());
                    } catch (Exception ex) {
                        log.warn("Storage binary cleanup failed for file {}: {}", fileId, ex.getMessage());
                    }

                    // Explicitly purge heavy child packet and threat rows to minimize memory footprint
                    if (file.getAnalyses() != null) {
                        for (Analysis analysis : file.getAnalyses()) {
                            packetRepository.deleteByAnalysisId(analysis.getId());
                            threatRepository.deleteByAnalysisId(analysis.getId());
                            analysisSummaryRepository.deleteByAnalysisId(analysis.getId());
                        }
                    }

                    uploadedFileRepository.delete(file);
                    return true;
                });

                if (Boolean.TRUE.equals(removed)) {
                    total++;
                }
            }

            if (ids.size() < batchSize) {
                break;
            }
        }
        return total;
    }
}
