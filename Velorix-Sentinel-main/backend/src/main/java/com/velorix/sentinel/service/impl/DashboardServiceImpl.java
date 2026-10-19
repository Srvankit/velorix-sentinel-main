package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.config.InspectionExecutorConfig;
import com.velorix.sentinel.dto.analysis.AnalysisResponse;
import com.velorix.sentinel.dto.dashboard.ActivityEntryResponse;
import com.velorix.sentinel.dto.dashboard.DashboardChartsResponse;
import com.velorix.sentinel.dto.dashboard.DashboardChartsResponse.NamedCount;
import com.velorix.sentinel.dto.dashboard.DashboardChartsResponse.TrendPoint;
import com.velorix.sentinel.dto.dashboard.DashboardChartsResponse.VolumePoint;
import com.velorix.sentinel.dto.dashboard.DashboardSummaryResponse;
import com.velorix.sentinel.dto.dashboard.SystemHealthResponse;
import com.velorix.sentinel.dto.dashboard.SystemHealthResponse.ComponentHealth;
import com.velorix.sentinel.dto.threat.ThreatResponse;
import com.velorix.sentinel.entity.ActivityLog;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.Threat;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatStatus;
import com.velorix.sentinel.mapper.AnalysisMapper;
import com.velorix.sentinel.mapper.DashboardMapper;
import com.velorix.sentinel.mapper.ThreatMapper;
import com.velorix.sentinel.repository.ActivityLogRepository;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.PacketRepository;
import com.velorix.sentinel.repository.ThreatRepository;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.security.CurrentUserProvider;
import com.velorix.sentinel.service.DashboardService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates the dashboard read models out of the persisted domain.
 *
 * <p>Nothing here invents data: an account without captures gets zeros and empty series.
 * Timelines are bucketed in memory so the aggregation stays portable across databases
 * and bounded by the row caps below.</p>
 */
@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardServiceImpl.class);

    /** Upper bounds so a huge account can never turn a dashboard load into a full scan. */
    private static final int MAX_TIMELINE_ROWS = 20_000;
    private static final int TOP_HOSTS = 5;
    private static final int MAX_LIST_LIMIT = 50;
    private static final List<ThreatStatus> ACTIVE_STATUSES =
            List.of(ThreatStatus.OPEN, ThreatStatus.ACKNOWLEDGED);

    private final AnalysisRepository analysisRepository;
    private final ThreatRepository threatRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final PacketRepository packetRepository;
    private final ActivityLogRepository activityLogRepository;
    private final AnalysisMapper analysisMapper;
    private final ThreatMapper threatMapper;
    private final DashboardMapper dashboardMapper;
    private final CurrentUserProvider currentUserProvider;
    private final Executor inspectionExecutor;

    @PersistenceContext
    private EntityManager entityManager;

    public DashboardServiceImpl(
            AnalysisRepository analysisRepository,
            ThreatRepository threatRepository,
            UploadedFileRepository uploadedFileRepository,
            PacketRepository packetRepository,
            ActivityLogRepository activityLogRepository,
            AnalysisMapper analysisMapper,
            ThreatMapper threatMapper,
            DashboardMapper dashboardMapper,
            CurrentUserProvider currentUserProvider,
            @Qualifier(InspectionExecutorConfig.EXECUTOR_BEAN) Executor inspectionExecutor) {
        this.analysisRepository = analysisRepository;
        this.threatRepository = threatRepository;
        this.uploadedFileRepository = uploadedFileRepository;
        this.packetRepository = packetRepository;
        this.activityLogRepository = activityLogRepository;
        this.analysisMapper = analysisMapper;
        this.threatMapper = threatMapper;
        this.dashboardMapper = dashboardMapper;
        this.currentUserProvider = currentUserProvider;
        this.inspectionExecutor = inspectionExecutor;
    }

    @Override
    public DashboardSummaryResponse summary() {
        UUID scope = currentUserProvider.ownerScope();

        Map<AnalysisStatus, Long> statuses = new EnumMap<>(AnalysisStatus.class);
        for (Object[] row : analysisRepository.statusHistogram(scope)) {
            statuses.put((AnalysisStatus) row[0], (Long) row[1]);
        }

        Map<ThreatSeverity, Long> severities = severityCounts(scope);
        long totalThreats = severities.values().stream().mapToLong(Long::longValue).sum();

        Double averageRisk = analysisRepository.averageRiskScoreForOwner(scope);
        int securityScore = averageRisk == null
                ? 100
                : Math.max(0, Math.min(100, 100 - (int) Math.round(averageRisk)));

        return new DashboardSummaryResponse(
                securityScore,
                analysisRepository.countForOwner(scope),
                statuses.getOrDefault(AnalysisStatus.COMPLETED, 0L),
                statuses.getOrDefault(AnalysisStatus.QUEUED, 0L)
                        + statuses.getOrDefault(AnalysisStatus.PROCESSING, 0L),
                totalThreats,
                severities.getOrDefault(ThreatSeverity.CRITICAL, 0L),
                severities.getOrDefault(ThreatSeverity.HIGH, 0L),
                uploadedFileRepository.countForOwner(scope),
                threatRepository.countByOwnerAndStatuses(scope, ACTIVE_STATUSES),
                analysisRepository.totalPacketsForOwner(scope));
    }

    @Override
    public DashboardChartsResponse charts() {
        UUID scope = currentUserProvider.ownerScope();
        Pageable cap = PageRequest.of(0, MAX_TIMELINE_ROWS);

        List<NamedCount> severity = severityCounts(scope).entrySet().stream()
                .map(entry -> new NamedCount(entry.getKey().name(), entry.getValue()))
                .toList();

        List<NamedCount> protocols = packetRepository.protocolDistributionForOwner(scope).stream()
                .map(row -> new NamedCount(row.getProtocol().name(), row.getTotal()))
                .toList();

        return new DashboardChartsResponse(
                threatTrend(threatRepository.detectionTimeline(scope, null, cap)),
                protocols,
                severity,
                packetVolume(analysisRepository.packetVolumeTimeline(scope, cap)),
                hostCounts(threatRepository.topSources(scope, null, PageRequest.of(0, TOP_HOSTS))),
                hostCounts(threatRepository.topDestinations(scope, null, PageRequest.of(0, TOP_HOSTS))));
    }

    @Override
    public List<AnalysisResponse> recentAnalyses(int limit) {
        UUID scope = currentUserProvider.ownerScope();
        Pageable page = PageRequest.of(0, clamp(limit), Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Analysis> analyses = analysisRepository.search(scope, null, null, page).getContent();
        return analysisMapper.toResponseList(analyses);
    }

    @Override
    public List<ThreatResponse> recentThreats(int limit) {
        UUID scope = currentUserProvider.ownerScope();
        Pageable page = PageRequest.of(0, clamp(limit), Sort.by(Sort.Direction.DESC, "detectedAt"));
        List<Threat> threats = threatRepository
                .search(scope, null, null, null, null, null, null, null, page)
                .getContent();
        return threatMapper.toResponseList(threats);
    }

    @Override
    public List<ActivityEntryResponse> activity(int limit) {
        Pageable page = PageRequest.of(0, clamp(limit));
        UUID scope = currentUserProvider.ownerScope();
        List<ActivityLog> logs = scope == null
                ? activityLogRepository.findAllByOrderByCreatedAtDesc(page).getContent()
                : activityLogRepository.findAllByUserIdOrderByCreatedAtDesc(scope, page).getContent();
        return dashboardMapper.toActivityEntries(logs);
    }

    @Override
    public SystemHealthResponse systemHealth() {
        return new SystemHealthResponse(
                ComponentHealth.up("API", "Serving requests"),
                databaseHealth(),
                workerHealth(),
                Instant.now());
    }

    /* ------------------------------------------------------------------ internals */

    private ComponentHealth databaseHealth() {
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            return ComponentHealth.up("Database", "PostgreSQL reachable");
        } catch (RuntimeException exception) {
            log.warn("Database health probe failed", exception);
            return ComponentHealth.down("Database", "Query probe failed");
        }
    }

    private ComponentHealth workerHealth() {
        if (!(inspectionExecutor instanceof ThreadPoolTaskExecutor pool)) {
            return ComponentHealth.up("Inspection worker", "Running");
        }
        int queued = pool.getThreadPoolExecutor().getQueue().size();
        int active = pool.getActiveCount();
        String detail = active + " running, " + queued + " queued";
        return queued >= pool.getQueueCapacity()
                ? ComponentHealth.degraded("Inspection worker", "Queue saturated: " + detail)
                : ComponentHealth.up("Inspection worker", detail);
    }

    private Map<ThreatSeverity, Long> severityCounts(UUID scope) {
        Map<ThreatSeverity, Long> counts = new EnumMap<>(ThreatSeverity.class);
        for (Object[] row : threatRepository.severityHistogram(scope, null)) {
            counts.put((ThreatSeverity) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static List<NamedCount> hostCounts(List<Object[]> rows) {
        List<NamedCount> counts = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            counts.add(new NamedCount((String) row[0], (Long) row[1]));
        }
        return List.copyOf(counts);
    }

    /** Daily buckets of findings, split by severity so the chart can stack them. */
    private static List<TrendPoint> threatTrend(List<Object[]> rows) {
        Map<Instant, long[]> buckets = new TreeMap<>();
        for (Object[] row : rows) {
            Instant detectedAt = (Instant) row[0];
            if (detectedAt == null) {
                continue;
            }
            ThreatSeverity severity = (ThreatSeverity) row[1];
            long[] bucket = buckets.computeIfAbsent(detectedAt.truncatedTo(ChronoUnit.DAYS), key -> new long[5]);
            bucket[0]++;
            switch (severity) {
                case CRITICAL -> bucket[1]++;
                case HIGH -> bucket[2]++;
                case MEDIUM -> bucket[3]++;
                default -> bucket[4]++;
            }
        }
        return buckets.entrySet().stream()
                .map(entry -> new TrendPoint(
                        entry.getKey(),
                        entry.getValue()[0],
                        entry.getValue()[1],
                        entry.getValue()[2],
                        entry.getValue()[3],
                        entry.getValue()[4]))
                .toList();
    }

    private static List<VolumePoint> packetVolume(List<Object[]> rows) {
        Map<Instant, Long> buckets = new TreeMap<>();
        for (Object[] row : rows) {
            Instant completedAt = (Instant) row[0];
            long packets = row[1] == null ? 0L : ((Number) row[1]).longValue();
            if (completedAt == null) {
                continue;
            }
            buckets.merge(completedAt.truncatedTo(ChronoUnit.DAYS), packets, Long::sum);
        }
        return buckets.entrySet().stream()
                .map(entry -> new VolumePoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(MAX_LIST_LIMIT, limit));
    }
}
