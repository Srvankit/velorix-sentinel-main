package com.velorix.sentinel.service;

import com.velorix.sentinel.common.PageResponse;
import com.velorix.sentinel.dto.common.PageRequestParams;
import com.velorix.sentinel.dto.packet.PacketDetailResponse;
import com.velorix.sentinel.dto.packet.PacketFilter;
import com.velorix.sentinel.dto.packet.PacketResponse;
import java.util.UUID;

/**
 * Read surface over decoded frames. Always paginated: a capture can hold millions of rows.
 */
public interface PacketService {

    PageResponse<PacketResponse> listByAnalysis(UUID analysisId, PacketFilter filter, PageRequestParams pageParams);

    /** Every decoded field of one frame, gated on ownership of the parent analysis. */
    PacketDetailResponse getDetail(UUID analysisId, UUID packetId);
}
