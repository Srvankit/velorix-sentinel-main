package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.common.PageResponse;
import com.velorix.sentinel.detection.DetectionRuleRegistry;
import com.velorix.sentinel.detection.RiskScorer;
import com.velorix.sentinel.dto.common.PageRequestParams;
import com.velorix.sentinel.dto.threat.DetectionRuleResponse;
import com.velorix.sentinel.dto.threat.ThreatFilter;
import com.velorix.sentinel.dto.threat.ThreatResponse;
import com.velorix.sentinel.dto.threat.ThreatStatsResponse;
import com.velorix.sentinel.entity.Threat;
import com.velorix.sentinel.entity.enums.ThreatSeverity;
import com.velorix.sentinel.entity.enums.ThreatType;
import com.velorix.sentinel.exception.ResourceNotFoundException;
import com.velorix.sentinel.mapper.ThreatMapper;
import com.velorix.sentinel.repository.ThreatRepository;
import com.velorix.sentinel.security.CurrentUserProvider;
import com.velorix.sentinel.service.ThreatService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-scoped reads over the threat feed.
 */
@Service
@Transactional(readOnly = true)
public class ThreatServiceImpl implements ThreatService {

    private static final Logger log = LoggerFactory.getLogger(ThreatServiceImpl.class);
    private static final String RESOURCE = "Threat";
    private static final int TOP_HOSTS = 10;
    /** Ceiling on rows pulled to build the timeline; findings are bounded by design. */
    private static final int TIMELINE_SAMPLE_LIMIT = 5_000;
    private static final int TIMELINE_BUCKETS = 24;

    private final ThreatRepository threatRepository;
    private final ThreatMapper threatMapper;
    private final CurrentUserProvider currentUserProvider;
    private final DetectionRuleRegistry ruleRegistry;

    public ThreatServiceImpl(
            ThreatRepository threatRepository,
            ThreatMapper threatMapper,
            CurrentUserProvider currentUserProvider,
            DetectionRuleRegistry ruleRegistry) {
        this.threatRepository = threatRepository;
        this.threatMapper = threatMapper;
        this.currentUserProvider = currentUserProvider;
        this.ruleRegistry = ruleRegistry;
    }

    @Override
    public PageResponse<ThreatResponse> list(ThreatFilter filter, PageRequestParams pageParams) {
        UUID ownerScope = currentUserProvider.ownerScope();
        Page<Threat> page = threatRepository.search(
                ownerScope,
                filter == null ? null : filter.analysisId(),
                filter == null ? null : filter.severity(),
                filter == null ? null : filter.threatType(),
                filter == null ? null : filter.status(),
                blankToNull(filter == null ? null : filter.detectionRule()),
                filter == null ? null : filter.minConfidence(),
                filter == null ? null : filter.searchPattern(),
                pageParams.toPageable());
        log.debug("Listed {} threats (ownerScope={})", page.getTotalElements(), ownerScope);
        return PageResponse.from(page, threatMapper::toResponse);
    }

    @Override
    public ThreatResponse getById(UUID threatId) {
        UUID ownerScope = currentUserProvider.ownerScope();
        Threat threat = (ownerScope == null
                ? threatRepository.findById(threatId)
                : threatRepository.findByIdAndOwner(threatId, ownerScope))
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, threatId));
        return threatMapper.toResponse(threat);
    }

    @Override
    public ThreatStatsResponse stats(UUID analysisId) {
        UUID ownerScope = currentUserProvider.ownerScope();

        Map<ThreatSeverity, Long> severityCounts = new EnumMap<>(ThreatSeverity.class);
        for (ThreatSeverity severity : ThreatSeverity.values()) {
            severityCounts.put(severity, 0L);
        }
        threatRepository.severityHistogram(ownerScope, analysisId)
                .forEach(row -> severityCounts.put((ThreatSeverity) row[0], (Long) row[1]));

        Map<ThreatType, Long> categoryCounts = new LinkedHashMap<>();
        threatRepository.categoryHistogram(ownerScope, analysisId)
                .forEach(row -> categoryCounts.put((ThreatType) row[0], (Long) row[1]));

        long critical = severityCounts.getOrDefault(ThreatSeverity.CRITICAL, 0L);
        long high = severityCounts.getOrDefault(ThreatSeverity.HIGH, 0L);
        long medium = severityCounts.getOrDefault(ThreatSeverity.MEDIUM, 0L);
        long low = severityCounts.getOrDefault(ThreatSeverity.LOW, 0L);
        long total = critical + high + medium + low;
        int riskScore = RiskScorer.score(critical, high, medium, low);

        return new ThreatStatsResponse(
                total,
                riskScore,
                RiskScorer.status(riskScore, critical, high),
                severityCounts,
                categoryCounts,
                hostCounts(threatRepository.topSources(ownerScope, analysisId, PageRequest.of(0, TOP_HOSTS))),
                hostCounts(threatRepository.topDestinations(ownerScope, analysisId, PageRequest.of(0, TOP_HOSTS))),
                timeline(ownerScope, analysisId));
    }

    @Override
    public List<DetectionRuleResponse> rules() {
        return ruleRegistry.allRules().stream()
                .map(rule -> threatMapper.toRuleResponse(rule, ruleRegistry.isEnabled(rule.metadata().id())))
                .toList();
    }

    private List<ThreatStatsResponse.HostCount> hostCounts(List<Object[]> rows) {
        List<ThreatStatsResponse.HostCount> counts = new ArrayList<>(rows.size());
        rows.forEach(row -> counts.add(new ThreatStatsResponse.HostCount((String) row[0], (Long) row[1])));
        return counts;
    }

    /**
     * Buckets findings into a fixed number of equal slices spanning the detection window.
     *
     * <p>Bucketing in memory rather than in SQL keeps the query portable across PostgreSQL
     * and the H2 database used by the integration tests, and the row count is bounded by
     * the engine's per-rule finding ceiling.</p>
     */
    private List<ThreatStatsResponse.TimelineBucket> timeline(UUID ownerScope, UUID analysisId) {
        List<Object[]> rows = threatRepository.detectionTimeline(
                ownerScope, analysisId, PageRequest.of(0, TIMELINE_SAMPLE_LIMIT));
        if (rows.isEmpty()) {
            return List.of();
        }
        Instant first = (Instant) rows.get(0)[0];
        Instant last = (Instant) rows.get(rows.size() - 1)[0];
        long spanMs = Math.max(Duration.between(first, last).toMillis(), 1L);
        long bucketMs = Math.max(spanMs / TIMELINE_BUCKETS, 1L);

        Map<Long, long[]> buckets = new TreeMap<>();
        for (Object[] row : rows) {
            Instant at = (Instant) row[0];
            ThreatSeverity severity = (ThreatSeverity) row[1];
            long index = (at.toEpochMilli() - first.toEpochMilli()) / bucketMs;
            long[] counters = buckets.computeIfAbsent(index, ignored -> new long[5]);
            counters[0]++;
            switch (severity) {
                case CRITICAL -> counters[1]++;
                case HIGH -> counters[2]++;
                case MEDIUM -> counters[3]++;
                case LOW -> counters[4]++;
            }
        }

        List<ThreatStatsResponse.TimelineBucket> timeline = new ArrayList<>(buckets.size());
        buckets.forEach((index, counters) -> timeline.add(new ThreatStatsResponse.TimelineBucket(
                first.plusMillis(index * bucketMs),
                counters[0], counters[1], counters[2], counters[3], counters[4])));
        return timeline;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
