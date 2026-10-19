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
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9.]{0,49}$", message = "Sort property contains illegal characters")
        String sort,

        @Schema(description = "Sort direction", allowableValues = {"ASC", "DESC"}, defaultValue = "DESC")
        @Pattern(regexp = "(?i)^(asc|desc)$", message = "Direction must be ASC or DESC")
        String direction) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String DEFAULT_SORT = "createdAt";

    /** Builds a Spring {@link Pageable}, applying safe defaults for absent values. */
    public Pageable toPageable() {
        Sort.Direction resolved = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = (sort == null || sort.isBlank()) ? DEFAULT_SORT : sort;
        return PageRequest.of(
                page == null ? DEFAULT_PAGE : page,
                size == null ? DEFAULT_SIZE : size,
                Sort.by(resolved, property));
    }
}
