package com.velorix.sentinel.controller;

import com.velorix.sentinel.common.ApiResponse;
import com.velorix.sentinel.common.PageResponse;
import com.velorix.sentinel.constants.ApiConstants;
import com.velorix.sentinel.dto.common.PageRequestParams;
import com.velorix.sentinel.dto.file.UploadedFileFilter;
import com.velorix.sentinel.dto.file.UploadedFileResponse;
import com.velorix.sentinel.service.UploadedFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ingestion and management API for uploaded network captures.
 */
@RestController
@RequestMapping(ApiConstants.FILES_BASE)
@Tag(name = "Files", description = "Uploaded network captures")
@SecurityRequirement(name = "bearerAuth")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final UploadedFileService uploadedFileService;

    public FileController(UploadedFileService uploadedFileService) {
        this.uploadedFileService = uploadedFileService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a capture",
            description = "Accepts a single .pcap or .pcapng capture (max 100 MB). The file is validated "
                    + "for extension, MIME type, emptiness, corruption and SHA-256 duplication before it is "
                    + "stored. Internal storage paths are never exposed.",
            requestBody = @RequestBody(content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    encoding = @Encoding(name = "file", contentType = "application/octet-stream"),
                    schema = @Schema(type = "object", requiredProperties = "file"))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Capture stored"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Empty, corrupted or unsupported capture", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Authentication required", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "An identical capture already exists", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413",
                    description = "Capture exceeds the maximum allowed size", content = @Content)
    })
    public ResponseEntity<ApiResponse<UploadedFileResponse>> upload(
            @Parameter(description = "The .pcap or .pcapng capture")
            @RequestPart("file") MultipartFile file) {
        UploadedFileResponse response = uploadedFileService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Capture uploaded"));
    }

    @GetMapping
    @Operation(summary = "List uploaded captures",
            description = "Returns the captures visible to the caller. Administrators see every capture; "
                    + "all other roles see only their own. Deleted captures are excluded.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Captures returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public ResponseEntity<ApiResponse<PageResponse<UploadedFileResponse>>> list(
            @Valid UploadedFileFilter filter,
            @Valid PageRequestParams pageParams) {
        log.debug("Listing captures with filter {}", filter);
        return ResponseEntity.ok(ApiResponse.success(
                uploadedFileService.list(filter, pageParams), "Captures loaded"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single capture")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Capture returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Capture not found or not visible")
    })
    public ResponseEntity<ApiResponse<UploadedFileResponse>> getById(
            @Parameter(description = "Capture identifier") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(uploadedFileService.getById(id), "Capture loaded"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a capture",
            description = "Removes the stored binary and marks the capture as DELETED. Ownership is verified "
                    + "before anything is removed; non-owners receive a 404.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Capture deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Capture not found or not visible")
    })
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "Capture identifier") @PathVariable UUID id) {
        uploadedFileService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Capture deleted"));
    }
}
