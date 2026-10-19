package com.velorix.sentinel.mapper;

import com.velorix.sentinel.dto.dashboard.ActivityEntryResponse;
import com.velorix.sentinel.entity.ActivityLog;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapping for dashboard read models.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DashboardMapper {

    @Mapping(target = "occurredAt", source = "createdAt")
    ActivityEntryResponse toActivityEntry(ActivityLog log);

    List<ActivityEntryResponse> toActivityEntries(List<ActivityLog> logs);
}
