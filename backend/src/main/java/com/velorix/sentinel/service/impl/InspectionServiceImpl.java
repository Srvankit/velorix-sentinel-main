package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.config.InspectionExecutorConfig;
import com.velorix.sentinel.config.properties.InspectionProperties;
import com.velorix.sentinel.dto.analysis.AnalysisResponse;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.enums.ActivityType;
import com.velorix.sentinel.entity.enums.AnalysisStage;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.entity.enums.UploadStatus;
import com.velorix.sentinel.exception.ApiException;
import com.velorix.sentinel.exception.ResourceConflictException;
import com.velorix.sentinel.exception.ResourceNotFoundException;
import com.velorix.sentinel.inspection.InspectionEngine;
import com.velorix.sentinel.inspection.InspectionProgress;
import com.velorix.sentinel.mapper.AnalysisMapper;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.security.CurrentUserProvider;
import com.velorix.sentinel.service.ActivityLogService;
import com.velorix.sentinel.service.InspectionService;
import com.velorix.sentinel.storage.FileStorageService;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Orchestrates inspection runs: validates the request inside the caller's transaction,
 * then hands the long running pipeline to a background worker.
 *
 * <p>Transaction boundaries matter here. The HTTP request commits the {@code QUEUED} row
 * and returns; the worker then drives short, independent transactions through
 * {@link AnalysisRunStateService}. One long transaction spanning the whole run would hold
 * a connection for minutes and hide progress from pollers until the very end.</p>
 */
@Service
public class InspectionServiceImpl implements InspectionService {

    private static final Logger log = LoggerFactory.getLogger(InspectionServiceImpl.class);
    private static final String FILE_RESOURCE = "Capture";

    private final UploadedFileRepository uploadedFileRepository;
    private final AnalysisRepository analysisRepository;
    private final AnalysisRunStateService runState;
    private final FileStorageService storageService;
    private final InspectionEngine engine;
    private final AnalysisMapper analysisMapper;
    private final CurrentUserProvider currentUserProvider;
    private final ActivityLogService activityLogService;
    private final InspectionProperties properties;
    private final Executor executor;

    public InspectionServiceImpl(
            UploadedFileRepository uploadedFileRepository,
            AnalysisRepository analysisRepository,
            AnalysisRunStateService runState,
            FileStorageService storageService,
            InspectionEngine engine,
            AnalysisMapper analysisMapper,
            CurrentUserProvider currentUserProvider,
            ActivityLogService activityLogService,
            InspectionProperties properties,
            @Qualifier(InspectionExecutorConfig.EXECUTOR_BEAN) Executor executor) {
        this.uploadedFileRepository = uploadedFileRepository;
        this.analysisRepository = analysisRepository;
        this.runState = runState;
        this.storageService = storageService;
        this.engine = engine;
        this.analysisMapper = analysisMapper;
        this.currentUserProvider = currentUserProvider;
        this.activityLogService = activityLogService;
        this.properties = properties;
        this.executor = executor;
    }

    @Override
    @Transactional
    public AnalysisResponse start(UUID uploadedFileId) {
        UploadedFile file = loadOwnedCapture(uploadedFileId);
        requireInspectable(file);
        requireNoActiveRun(file);

        Analysis analysis = analysisRepository.save(Analysis.builder()
                .uploadedFile(file)
                .status(AnalysisStatus.QUEUED)
                .stage(AnalysisStage.PREPARING)
                .analysisVersion(properties.engineVersion())
                .build());

        activityLogService.success(
                file.getUser(),
                ActivityType.ANALYSIS_STARTED,
                "Inspection queued for capture %s".formatted(file.getOriginalFileName()));

        UUID analysisId = analysis.getId();
        String storageKey = file.getStoredFileName();
        long declaredSize = file.getFileSize();

        submitAfterCommit(() -> execute(analysisId, storageKey, declaredSize));
        log.info("Inspection {} queued for capture {} ({} bytes)", analysisId, uploadedFileId, declaredSize);
        return analysisMapper.toResponse(analysis);
    }

    /**
     * The whole pipeline, on a worker thread with no ambient transaction. Every failure
     * mode ends in a persisted {@code FAILED} row so a stalled run is never invisible.
     */
    void execute(UUID analysisId, String storageKey, long declaredSize) {
        Instant startedAt = Instant.now();
        try {
            runState.markProcessing(analysisId, startedAt);
            log.info("Inspection {} started", analysisId);

            InspectionProgress progress;
            try (InputStream capture = storageService.open(storageKey)) {
                progress = engine.inspect(
                        analysisId, capture, declaredSize, snapshot -> runState.saveProgress(analysisId, snapshot));
            }

            runState.complete(analysisId, progress, startedAt);
            log.info("Inspection {} completed: {} packets in {} ms",
                    analysisId, progress.processedPackets(), Duration.between(startedAt, Instant.now()).toMillis());
        } catch (Exception ex) {
            String reason = ex instanceof ApiException
                    ? ex.getMessage()
                    : "The capture could not be inspected: " + ex.getMessage();
            log.error("Inspection {} failed: {}", analysisId, reason, ex);
            runState.fail(analysisId, reason, startedAt);
        }
    }

    /* ---------------------------------------------------------------- validation */

    private UploadedFile loadOwnedCapture(UUID uploadedFileId) {
        UUID ownerScope = currentUserProvider.ownerScope();
        return uploadedFileRepository.findById(uploadedFileId)
                .filter(file -> ownerScope == null || file.getUser().getId().equals(ownerScope))
                .filter(file -> !file.getUploadStatus().isDeleted())
                .orElseThrow(() -> ResourceNotFoundException.of(FILE_RESOURCE, uploadedFileId));
    }

    private void requireInspectable(UploadedFile file) {
        if (file.getUploadStatus() != UploadStatus.READY_FOR_ANALYSIS
                && file.getUploadStatus() != UploadStatus.UPLOADED) {
            throw new ResourceConflictException(
                    "The capture is not ready for inspection (current status: %s)".formatted(file.getUploadStatus()));
        }
        if (!storageService.exists(file.getStoredFileName())) {
            throw new ResourceConflictException("The stored capture is missing and cannot be inspected");
        }
    }

    private void requireNoActiveRun(UploadedFile file) {
        if (analysisRepository.existsActiveForFile(file.getId())) {
            throw new ResourceConflictException("An inspection of this capture is already running");
        }
    }

    /* ---------------------------------------------------------------- scheduling */

    /** Defers submission until commit so the worker can never race the {@code QUEUED} row. */
    private void submitAfterCommit(Runnable task) {
        Runnable guarded = () -> {
            try {
                task.run();
            } catch (RuntimeException ex) {
                log.error("Inspection worker terminated unexpectedly", ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(guarded);
                }
            });
        } else {
            dispatch(guarded);
        }
    }

    private void dispatch(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ex) {
            log.error("Inspection could not be scheduled: {}", ex.getMessage());
        }
    }
}
