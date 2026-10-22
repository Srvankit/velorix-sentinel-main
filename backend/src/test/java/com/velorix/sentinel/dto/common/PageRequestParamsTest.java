package com.velorix.sentinel.dto.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.velorix.sentinel.exception.BadRequestException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageRequestParamsTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("Default parameters resolve to page 0, size 20, sort createdAt DESC")
    void defaultParametersResolveCorrectly() {
        PageRequestParams params = new PageRequestParams(null, null, null, null);
        Pageable pageable = params.toPageable();

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("Ascending direction is parsed case-insensitively")
    void ascendingDirectionParsedCaseInsensitively() {
        PageRequestParams params = new PageRequestParams(1, 50, "createdAt", "asc");
        Pageable pageable = params.toPageable();

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("Comma-separated sort expressions like 'createdAt,asc' are normalized before validation")
    void commaSeparatedSortExpressionNormalized() {
        PageRequestParams params = new PageRequestParams(0, 20, "createdAt,asc", null);

        assertThat(params.sort()).isEqualTo("createdAt");
        assertThat(params.direction()).isEqualTo("asc");

        Pageable pageable = params.toAnalysisPageable();
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);

        Set<ConstraintViolation<PageRequestParams>> violations = validator.validate(params);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Comma-separated sort expressions like 'packetNumber,desc' are normalized")
    void commaSeparatedPacketSortNormalized() {
        PageRequestParams params = new PageRequestParams(2, 100, "packetNumber,desc", null);

        assertThat(params.sort()).isEqualTo("packetNumber");
        assertThat(params.direction()).isEqualTo("desc");

        Pageable pageable = params.toPacketPageable();
        Sort.Order order = pageable.getSort().getOrderFor("packetNumber");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);

        Set<ConstraintViolation<PageRequestParams>> violations = validator.validate(params);
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "createdAt", "updatedAt", "totalPackets", "riskScore", "duration",
            "status", "stage", "processedPackets", "progressPercent"
    })
    @DisplayName("Analysis allowlist permits all 9 documented analysis sort fields")
    void analysisAllowlistPermitsValidFields(String property) {
        PageRequestParams params = new PageRequestParams(0, 20, property, "DESC");
        Pageable pageable = params.toAnalysisPageable();

        assertThat(pageable.getSort().getOrderFor(property)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "packetNumber", "packetTimestamp", "packetLength", "protocol",
            "sourceIp", "destinationIp", "sourcePort", "destinationPort", "isSuspicious"
    })
    @DisplayName("Packet allowlist permits all 9 documented packet sort fields")
    void packetAllowlistPermitsValidFields(String property) {
        PageRequestParams params = new PageRequestParams(0, 50, property, "ASC");
        Pageable pageable = params.toPacketPageable();

        String expectedProperty = switch (property) {
            case "packetTimestamp" -> "timestamp";
            case "isSuspicious" -> "suspicious";
            default -> property;
        };
        assertThat(pageable.getSort().getOrderFor(expectedProperty)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "detectedAt", "createdAt", "severity", "threatType", "confidenceScore", "category"
    })
    @DisplayName("Threat allowlist permits all 6 documented threat sort fields")
    void threatAllowlistPermitsValidFields(String property) {
        PageRequestParams params = new PageRequestParams(0, 20, property, "DESC");
        Pageable pageable = params.toThreatPageable();

        assertThat(pageable.getSort().getOrderFor(property)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "createdAt", "originalFileName", "fileSize", "uploadStatus"
    })
    @DisplayName("File allowlist permits all 4 documented file sort fields")
    void fileAllowlistPermitsValidFields(String property) {
        PageRequestParams params = new PageRequestParams(0, 20, property, "ASC");
        Pageable pageable = params.toFilePageable();

        assertThat(pageable.getSort().getOrderFor(property)).isNotNull();
    }

    @Test
    @DisplayName("Non-allowlisted property throws BadRequestException with rejected and allowed property details")
    void nonAllowlistedPropertyThrowsBadRequestException() {
        PageRequestParams params = new PageRequestParams(0, 20, "unsupportedField", "DESC");

        assertThatThrownBy(params::toAnalysisPageable)
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid sort property 'unsupportedField'")
                .hasMessageContaining("Allowed sort properties:");
    }

    @Test
    @DisplayName("Dotted property traversal fails bean validation pattern regex")
    void dottedTraversalFailsBeanValidation() {
        PageRequestParams params = new PageRequestParams(0, 20, "uploadedFile.user.password", "DESC");
        Set<ConstraintViolation<PageRequestParams>> violations = validator.validate(params);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("sort")
                        && v.getMessage().contains("Sort property contains illegal characters"));
    }

    @Test
    @DisplayName("SQL injection strings fail bean validation pattern regex")
    void sqlInjectionFailsBeanValidation() {
        PageRequestParams params = new PageRequestParams(0, 20, "createdAt; DROP TABLE users--", "DESC");
        Set<ConstraintViolation<PageRequestParams>> violations = validator.validate(params);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("sort"));
    }

    @Test
    @DisplayName("Validation fails when page index is negative")
    void negativePageIndexFailsValidation() {
        PageRequestParams params = new PageRequestParams(-1, 20, "createdAt", "DESC");
        Set<ConstraintViolation<PageRequestParams>> violations = validator.validate(params);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("page"));
    }

    @Test
    @DisplayName("Validation fails when page size is zero or exceeds 200")
    void invalidPageSizeFailsValidation() {
        Set<ConstraintViolation<PageRequestParams>> zeroSize = validator.validate(
                new PageRequestParams(0, 0, "createdAt", "DESC"));
        assertThat(zeroSize).anyMatch(v -> v.getPropertyPath().toString().equals("size"));

        Set<ConstraintViolation<PageRequestParams>> excessiveSize = validator.validate(
                new PageRequestParams(0, 201, "createdAt", "DESC"));
        assertThat(excessiveSize).anyMatch(v -> v.getPropertyPath().toString().equals("size"));
    }

    @Test
    @DisplayName("Validation fails when direction is not ASC or DESC")
    void invalidDirectionFailsValidation() {
        PageRequestParams params = new PageRequestParams(0, 20, "createdAt", "SIDEWAYS");
        Set<ConstraintViolation<PageRequestParams>> violations = validator.validate(params);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("direction"));
    }
}
