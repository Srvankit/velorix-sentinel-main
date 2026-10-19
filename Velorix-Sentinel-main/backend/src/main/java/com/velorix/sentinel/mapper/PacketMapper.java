package com.velorix.sentinel.mapper;

import com.velorix.sentinel.dto.packet.PacketDetailResponse;
import com.velorix.sentinel.dto.packet.PacketResponse;
import com.velorix.sentinel.entity.Packet;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapping between the {@link Packet} entity and its DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PacketMapper {

    @Mapping(target = "analysisId", source = "analysis.id")
    PacketResponse toResponse(Packet packet);

    List<PacketResponse> toResponseList(List<Packet> packets);

    @Mapping(target = "analysisId", source = "analysis.id")
    PacketDetailResponse toDetailResponse(Packet packet);
}
