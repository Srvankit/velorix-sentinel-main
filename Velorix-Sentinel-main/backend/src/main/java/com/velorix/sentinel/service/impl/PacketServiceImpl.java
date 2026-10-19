package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.common.PageResponse;
import com.velorix.sentinel.dto.common.PageRequestParams;
import com.velorix.sentinel.dto.packet.PacketDetailResponse;
import com.velorix.sentinel.dto.packet.PacketFilter;
import com.velorix.sentinel.dto.packet.PacketResponse;
import com.velorix.sentinel.entity.Packet;
import com.velorix.sentinel.exception.ResourceNotFoundException;
import com.velorix.sentinel.mapper.PacketMapper;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.PacketRepository;
import com.velorix.sentinel.security.CurrentUserProvider;
import com.velorix.sentinel.service.PacketService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paginated reads over decoded frames, gated on ownership of the parent analysis.
 */
@Service
@Transactional(readOnly = true)
public class PacketServiceImpl implements PacketService {

    private static final Logger log = LoggerFactory.getLogger(PacketServiceImpl.class);
    private static final String ANALYSIS_RESOURCE = "Analysis";

    private final PacketRepository packetRepository;
    private final AnalysisRepository analysisRepository;
    private final PacketMapper packetMapper;
    private final CurrentUserProvider currentUserProvider;

    public PacketServiceImpl(
            PacketRepository packetRepository,
            AnalysisRepository analysisRepository,
            PacketMapper packetMapper,
            CurrentUserProvider currentUserProvider) {
        this.packetRepository = packetRepository;
        this.analysisRepository = analysisRepository;
        this.packetMapper = packetMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public PageResponse<PacketResponse> listByAnalysis(
            UUID analysisId, PacketFilter filter, PageRequestParams pageParams) {
        requireVisibleAnalysis(analysisId);
        Page<Packet> page = packetRepository.search(
                analysisId,
                filter == null ? null : filter.protocol(),
                filter == null ? null : filter.networkProtocol(),
                filter == null ? null : filter.suspicious(),
                filter == null ? null : filter.likePattern(),
                pageParams.toPageable());
        log.debug("Listed {} packets for analysis {}", page.getTotalElements(), analysisId);
        return PageResponse.from(page, packetMapper::toResponse);
    }

    @Override
    public PacketDetailResponse getDetail(UUID analysisId, UUID packetId) {
        requireVisibleAnalysis(analysisId);
        return packetRepository.findByIdAndAnalysisId(packetId, analysisId)
                .map(packetMapper::toDetailResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("Packet", packetId));
    }

    private void requireVisibleAnalysis(UUID analysisId) {
        UUID ownerScope = currentUserProvider.ownerScope();
        boolean visible = ownerScope == null
                ? analysisRepository.existsById(analysisId)
                : analysisRepository.findByIdAndOwner(analysisId, ownerScope).isPresent();
        if (!visible) {
            throw ResourceNotFoundException.of(ANALYSIS_RESOURCE, analysisId);
        }
    }
}
