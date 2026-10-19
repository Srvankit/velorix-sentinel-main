package com.velorix.sentinel.service;

import com.velorix.sentinel.common.PageResponse;
import com.velorix.sentinel.dto.common.PageRequestParams;
import com.velorix.sentinel.dto.file.UploadedFileFilter;
import com.velorix.sentinel.dto.file.UploadedFileResponse;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ingestion and read surface over uploaded captures. Every operation is scoped to the
 * caller: administrators see everything, all other roles only their own captures.
 */
public interface UploadedFileService {

    /** Validates, stores and registers a capture for the authenticated user. */
    UploadedFileResponse upload(MultipartFile file);

    PageResponse<UploadedFileResponse> list(UploadedFileFilter filter, PageRequestParams pageParams);

    UploadedFileResponse getById(UUID fileId);

    /** Soft deletes the capture and removes its binary from storage. */
    void delete(UUID fileId);

    long countForCurrentUser();
}
