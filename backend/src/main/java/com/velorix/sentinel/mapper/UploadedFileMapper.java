package com.velorix.sentinel.mapper;

import com.velorix.sentinel.dto.file.UploadedFileResponse;
import com.velorix.sentinel.entity.UploadedFile;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapping between the {@link UploadedFile} aggregate and its DTOs.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UploadedFileMapper {

    @Mapping(target = "ownerId", source = "user.id")
    UploadedFileResponse toResponse(UploadedFile file);

    List<UploadedFileResponse> toResponseList(List<UploadedFile> files);
}
