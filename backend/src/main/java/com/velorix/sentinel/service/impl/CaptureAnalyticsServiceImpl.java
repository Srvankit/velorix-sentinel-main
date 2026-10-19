package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.common.PageResponse;
import com.velorix.sentinel.dto.analysis.ConversationResponse;
import com.velorix.sentinel.dto.analysis.NetworkSummaryResponse;
import com.velorix.sentinel.dto.analysis.ProtocolDistributionEntry;
import com.velorix.sentinel.dto.analysis.TopEntry;
import com.velorix.sentinel.dto.common.PageRequestParams;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.enums.Protocol;
import com.velorix.sentinel.exception.ResourceNotFoundException;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.PacketRepository;
import com.velorix.sentinel.repository.projection.ConversationRow;
import com.velorix.sentinel.repository.projection.EndpointCount;
import com.velorix.sentinel.repository.projection.PortCount;
import com.velorix.sentinel.security.CurrentUserProvider;
import com.velorix.sentinel.service.CaptureAnalyticsService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the analysis overview from persisted frames.
 *
 * <p>Every number is produced by an indexed aggregate over {@code packets}; nothing is
 * cached or estimated, so the numbers stay correct while a run is still streaming.</p>
 */
@Service
@Transactional(readOnly = true)
public class CaptureAnalyticsServiceImpl implements CaptureAnalyticsService {

    private static final String RESOURCE = "Analysis";
    private static final int TOP_N = 10;

    /** Native aggregate aliases the conversation listing may be ordered by. */
    private static final Map<String, String> CONVERSATION_SORTS = Map.of(
            "packets", "packets",
            "bytes", "bytes",
            "startedAt", "startedAtMs",
            "endedAt", "endedAtMs",
            "client", "clientIp",
            "server", "serverIp",
            "protocol", "protocol");

    private final PacketRepository packetRepository;
    private final AnalysisRepository analysisRepository;
    private final CurrentUserProvider currentUserProvider;

    public CaptureAnalyticsServiceImpl(
            PacketRepository packetRepository,
            AnalysisRepository analysisRepository,
            CurrentUserProvider currentUserProvider) {
        this.packetRepository = packetRepository;
        this.analysisRepository = analysisRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public NetworkSummaryResponse networkSummary(UUID analysisId) {
        Analysis analysis = loadOwned(analysisId);

        long totalPackets = packetRepository.countByAnalysisId(analysisId);
        long totalBytes = packetRepository.totalBytes(analysisId);
        Long captureDurationMs = captureDurationMs(analysis);

        List<ProtocolDistributionEntry> protocols = packetRepository.protocolDistribution(analysisId).stream()
                .map(bucket -> ProtocolDistributionEntry.of(bucket.getProtocol(), bucket.getTotal(), totalPackets))
                .toList();

        Pageable topN = PageRequest.of(0, TOP_N);
        List<EndpointCount> sources = packetRepository.topSourceIps(analysisId, topN);
        List<EndpointCount> destinations = packetRepository.topDestinationIps(analysisId, topN);
        List<PortCount> ports = packetRepository.topDestinationPorts(analysisId, topN);

        double seconds = captureDurationMs == null || captureDurationMs <= 0 ? 0d : captureDurationMs / 1000d;

        return new NetworkSummaryResponse(
                analysisId,
                totalPackets,
                analysis.getMalformedPackets(),
                packetRepository.countByAnalysisIdAndSuspiciousTrue(analysisId),
                totalBytes,
                totalPackets == 0 ? 0d : Math.round((totalBytes * 100d) / totalPackets) / 100d,
                packetRepository.countDistinctSourceIps(analysisId),
                packetRepository.countDistinctDestinationIps(analysisId),
                packetRepository.countDistinctAddresses(analysisId),
                packetRepository.countDistinctPorts(analysisId),
                analysis.getCaptureStartedAt(),
                analysis.getCaptureEndedAt(),
                captureDurationMs,
                analysis.getDuration(),
                seconds <= 0 ? 0d : round2(totalPackets / seconds),
                seconds <= 0 ? 0d : round2((totalBytes * 8d) / seconds),
                protocols,
                toEntries(sources, totalPackets),
                toEntries(destinations, totalPackets),
                topTalkers(sources, destinations, totalPackets),
                ports.stream()
                        .map(port -> TopEntry.of(
                                String.valueOf(port.getPort()), port.getTotal(), port.getBytes(), totalPackets))
                        .toList());
    }

    @Override
    public PageResponse<ConversationResponse> conversations(UUID analysisId, PageRequestParams pageParams) {
        loadOwned(analysisId);
        Page<ConversationRow> page = packetRepository.conversations(analysisId, conversationPageable(pageParams));
        return PageResponse.from(page, this::toConversation);
    }

    /** Maps the generic page params onto the whitelisted native aliases. */
    private Pageable conversationPageable(PageRequestParams pageParams) {
        Pageable requested = pageParams == null ? PageRequest.of(0, 20) : pageParams.toPageable();
        String requestedSort = requested.getSort().stream().findFirst().map(Sort.Order::getProperty).orElse("packets");
        String column = CONVERSATION_SORTS.getOrDefault(requestedSort, "packets");
        Sort.Direction direction = requested.getSort().stream()
                .findFirst()
                .map(Sort.Order::getDirection)
                .orElse(Sort.Direction.DESC);
        return PageRequest.of(
                requested.getPageNumber(), requested.getPageSize(), Sort.by(direction, column));
    }

    private ConversationResponse toConversation(ConversationRow row) {
        Instant startedAt = toInstant(row.getStartedAtMs());
        Instant endedAt = toInstant(row.getEndedAtMs());
        Long durationMs = startedAt == null || endedAt == null
                ? null
                : Math.max(0L, endedAt.toEpochMilli() - startedAt.toEpochMilli());
        return new ConversationResponse(
                row.getClientIp(),
                row.getServerIp(),
                row.getServerPort(),
                parseProtocol(row.getProtocol()),
                row.getPackets(),
                row.getBytes(),
                row.getSuspiciousPackets(),
                startedAt,
                endedAt,
                durationMs,
                row.getSuspiciousPackets() > 0 ? "SUSPICIOUS" : "CLEAN");
    }

    /** Combined ranking: an endpoint's traffic in both directions. */
    private List<TopEntry> topTalkers(List<EndpointCount> sources, List<EndpointCount> destinations, long total) {
        Map<String, long[]> merged = new HashMap<>();
        for (EndpointCount entry : sources) {
            merged.computeIfAbsent(entry.getLabel(), key -> new long[2]);
            merged.get(entry.getLabel())[0] += entry.getTotal();
            merged.get(entry.getLabel())[1] += entry.getBytes();
        }
        for (EndpointCount entry : destinations) {
            merged.computeIfAbsent(entry.getLabel(), key -> new long[2]);
            merged.get(entry.getLabel())[0] += entry.getTotal();
            merged.get(entry.getLabel())[1] += entry.getBytes();
        }
        List<TopEntry> talkers = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : merged.entrySet()) {
            talkers.add(TopEntry.of(entry.getKey(), entry.getValue()[0], entry.getValue()[1], total));
        }
        talkers.sort(Comparator.comparingLong(TopEntry::packets).reversed());
        return talkers.size() > TOP_N ? talkers.subList(0, TOP_N) : talkers;
    }

    private static List<TopEntry> toEntries(List<EndpointCount> rows, long total) {
        return rows.stream()
                .map(row -> TopEntry.of(row.getLabel(), row.getTotal(), row.getBytes(), total))
                .toList();
    }

    private static Instant toInstant(Double epochMillis) {
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis.longValue());
    }

    private static Protocol parseProtocol(String raw) {
        if (raw == null) {
            return Protocol.OTHER;
        }
        return Set.of(Protocol.values()).stream()
                .filter(candidate -> candidate.name().equals(raw))
                .findFirst()
                .orElse(Protocol.OTHER);
    }

    private static Long captureDurationMs(Analysis analysis) {
        if (analysis.getCaptureDuration() != null) {
            return analysis.getCaptureDuration();
        }
        if (analysis.getCaptureStartedAt() == null || analysis.getCaptureEndedAt() == null) {
            return null;
        }
        return Math.max(
                0L,
                analysis.getCaptureEndedAt().toEpochMilli() - analysis.getCaptureStartedAt().toEpochMilli());
    }

    private static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private Analysis loadOwned(UUID analysisId) {
        UUID ownerScope = currentUserProvider.ownerScope();
        return (ownerScope == null
                ? analysisRepository.findWithDetailsById(analysisId)
                : analysisRepository.findByIdAndOwner(analysisId, ownerScope))
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, analysisId));
    }
}
