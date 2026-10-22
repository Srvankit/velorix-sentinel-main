package com.velorix.sentinel.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Reusable, validated pagination and sorting parameters for every list endpoint.
 */
@Schema(name = "PageRequestParams", description = "Pagination and sorting parameters")
public record PageRequestParams(
        @Schema(description = "Zero based page index", defaultValue = "0")
        @Min(value = 0, message = "Page index must not be negative")
        Integer page,

        @Schema(description = "Page size", defaultValue = "20")
        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 200, message = "Page size must not exceed 200")
        Integer size,

        @Schema(description = "Property to sort by", defaultValue = "createdAt")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{0,49}$", message = "Sort property contains illegal characters")
        String sort,

        @Schema(description = "Sort direction", allowableValues = {"ASC", "DESC"}, defaultValue = "DESC")
        @Pattern(regexp = "(?i)^(asc|desc)$", message = "Direction must be ASC or DESC")
        String direction) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final String DEFAULT_SORT = "createdAt";

    public static final java.util.Set<String> ALLOWED_ANALYSIS_SORTS = java.util.Set.of(
            "createdAt", "updatedAt", "totalPackets", "riskScore", "duration",
            "status", "stage", "processedPackets", "progressPercent");

    public static final java.util.Set<String> ALLOWED_PACKET_SORTS = java.util.Set.of(
            "packetNumber", "packetTimestamp", "packetLength", "protocol",
            "sourceIp", "destinationIp", "sourcePort", "destinationPort", "isSuspicious");

    public static final java.util.Set<String> ALLOWED_THREAT_SORTS = java.util.Set.of(
            "detectedAt", "createdAt", "severity", "threatType", "confidenceScore", "category");

    public static final java.util.Set<String> ALLOWED_FILE_SORTS = java.util.Set.of(
            "createdAt", "originalFileName", "fileSize", "uploadStatus");

    public static final java.util.Set<String> ALLOWED_CONVERSATION_SORTS = java.util.Set.of(
            "packets", "bytes", "startedAt", "endedAt", "client", "server", "protocol");

    /**
     * Compact constructor normalizing composite sort strings (e.g., "createdAt,asc")
     * into separate property and direction fields before Bean Validation executes.
     */
    public PageRequestParams {
        if (sort != null && sort.contains(",")) {
            String[] parts = sort.split(",", 2);
            sort = parts[0].trim();
            if ((direction == null || direction.isBlank()) && parts.length > 1 && !parts[1].isBlank()) {
                direction = parts[1].trim();
            }
        }
    }

    /**
     * Builds a Spring {@link Pageable}, validating the requested sort property
     * against an entity-specific allowlist and applying the provided default sort property.
     *
     * @param allowedSorts Set of permitted entity sort property names
     * @param defaultSort  Default property name to use when none is specified
     * @return Validated {@link Pageable} instance
     * @throws com.velorix.sentinel.exception.BadRequestException if the sort property is not in {@code allowedSorts}
     */
    public Pageable toPageable(java.util.Set<String> allowedSorts, String defaultSort) {
        String resolvedSort = (sort == null || sort.isBlank()) ? defaultSort : sort;
        if (allowedSorts != null && !allowedSorts.isEmpty() && !allowedSorts.contains(resolvedSort)) {
            throw new com.velorix.sentinel.exception.BadRequestException(String.format(
                    "Invalid sort property '%s'. Allowed sort properties: %s",
                    resolvedSort,
                    allowedSorts.stream().sorted().toList()));
        }
        Sort.Direction resolvedDir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(
                page == null ? DEFAULT_PAGE : page,
                size == null ? DEFAULT_SIZE : size,
                resolvedSort == null || resolvedSort.isBlank()
                        ? Sort.unsorted()
                        : Sort.by(resolvedDir, resolvedSort));
    }

    /** Overload applying an entity-specific allowlist with the platform default sort ("createdAt"). */
    public Pageable toPageable(java.util.Set<String> allowedSorts) {
        return toPageable(allowedSorts, DEFAULT_SORT);
    }

    /** Builds a Spring {@link Pageable}, applying safe defaults for absent values without allowlist restriction. */
    public Pageable toPageable() {
        return toPageable(null, DEFAULT_SORT);
    }

    public Pageable toAnalysisPageable() {
        return toPageable(ALLOWED_ANALYSIS_SORTS, "createdAt");
    }

    public Pageable toPacketPageable() {
        Pageable pageable = toPageable(ALLOWED_PACKET_SORTS, "packetNumber");
        Sort.Order order = pageable.getSort().stream().findFirst().orElse(null);
        if (order == null) {
            return pageable;
        }
        String property = order.getProperty();
        String mappedProperty = switch (property) {
            case "packetTimestamp" -> "timestamp";
            case "isSuspicious" -> "suspicious";
            default -> property;
        };
        if (mappedProperty.equals(property)) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(order.getDirection(), mappedProperty));
    }

    public Pageable toThreatPageable() {
        return toPageable(ALLOWED_THREAT_SORTS, "detectedAt");
    }

    public Pageable toFilePageable() {
        return toPageable(ALLOWED_FILE_SORTS, "createdAt");
    }
}
