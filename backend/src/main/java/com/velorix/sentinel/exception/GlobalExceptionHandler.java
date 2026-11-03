package com.velorix.sentinel.exception;

import com.velorix.sentinel.common.ApiError;
import com.velorix.sentinel.common.ApiResponse;
import com.velorix.sentinel.constants.MessageConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every exception into the platform's single {@link ApiResponse} envelope.
 *
 * <p>Enforces clean HTTP status codes (4xx for client and protocol errors, 5xx
 * strictly for unexpected internal server errors). Common protocol errors are logged
 * at DEBUG/WARN to prevent error log pollution.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex, HttpServletRequest request) {
        log.warn("Handled API exception [{}] on {} {}: {}",
                ex.getCode(), request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(ex.getStatus(), ex.getMessage(), List.of(ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(ApiError.ofField(fieldError.getField(), "VALIDATION_ERROR", fieldError.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                errors.add(ApiError.of("VALIDATION_ERROR", error.getDefaultMessage())));
        return build(HttpStatus.BAD_REQUEST, MessageConstants.VALIDATION_FAILED, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiError> errors = ex.getConstraintViolations().stream()
                .map(violation -> ApiError.ofField(
                        String.valueOf(violation.getPropertyPath()), "VALIDATION_ERROR", violation.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, MessageConstants.VALIDATION_FAILED, errors);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        log.warn("HTTP method not allowed [{}] on {} {}: {}",
                ex.getMethod(), request.getMethod(), request.getRequestURI(), ex.getMessage());
        String message = "Request method '" + ex.getMethod() + "' is not supported for this endpoint";
        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(headers)
                .body(ApiResponse.error(message, HttpStatus.METHOD_NOT_ALLOWED,
                        List.of(ApiError.of("METHOD_NOT_ALLOWED", message))));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        log.warn("Unsupported media type on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        String message = ex.getContentType() != null
                ? "Content-Type '" + ex.getContentType() + "' is not supported"
                : MessageConstants.UNSUPPORTED_MEDIA_TYPE;
        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedMediaTypes() != null && !ex.getSupportedMediaTypes().isEmpty()) {
            headers.setAccept(ex.getSupportedMediaTypes());
        }
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .headers(headers)
                .body(ApiResponse.error(message, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        List.of(ApiError.of("UNSUPPORTED_MEDIA_TYPE", message))));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request) {
        log.warn("Not acceptable media type requested on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        String message = "Could not find acceptable representation for the requested media type";
        return build(HttpStatus.NOT_ACCEPTABLE, message,
                List.of(ApiError.of("NOT_ACCEPTABLE", message)));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestPart(
            MissingServletRequestPartException ex,
            HttpServletRequest request) {
        log.warn("Missing request part [{}] on {} {}",
                ex.getRequestPartName(), request.getMethod(), request.getRequestURI());
        String message = "Required request part '" + ex.getRequestPartName() + "' is missing";
        return build(HttpStatus.BAD_REQUEST, message,
                List.of(ApiError.ofField(ex.getRequestPartName(), "MISSING_REQUEST_PART", message)));
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiResponse<Void>> handlePropertyReference(
            PropertyReferenceException ex,
            HttpServletRequest request) {
        log.warn("Invalid property reference [{}] on {} {}: {}",
                ex.getPropertyName(), request.getMethod(), request.getRequestURI(), ex.getMessage());
        String message = "No property '" + ex.getPropertyName() + "' found for type '"
                + ex.getType().getType().getSimpleName() + "'";
        return build(HttpStatus.BAD_REQUEST, message,
                List.of(ApiError.ofField(ex.getPropertyName(), "INVALID_PROPERTY", message)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        log.warn("Argument type mismatch [{}] on {} {}: {}",
                ex.getName(), request.getMethod(), request.getRequestURI(), ex.getMessage());
        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "valid format";
        String message = "Failed to convert parameter '" + ex.getName() + "' to required type '" + requiredType + "'";
        return build(HttpStatus.BAD_REQUEST, message,
                List.of(ApiError.ofField(ex.getName(), "TYPE_MISMATCH", message)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        log.warn("Missing parameter [{}] on {} {}",
                ex.getParameterName(), request.getMethod(), request.getRequestURI());
        String message = "Required request parameter '" + ex.getParameterName() + "' is missing";
        return build(HttpStatus.BAD_REQUEST, message,
                List.of(ApiError.ofField(ex.getParameterName(), "MISSING_PARAMETER", message)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.debug("Malformed request body on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, MessageConstants.MALFORMED_REQUEST,
                List.of(ApiError.of("MALFORMED_REQUEST", MessageConstants.MALFORMED_REQUEST)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Rejected oversized upload: {}", ex.getMessage());
        String message = "The upload exceeds the maximum allowed capture size.";
        return build(HttpStatus.PAYLOAD_TOO_LARGE, message, List.of(ApiError.of("FILE_TOO_LARGE", message)));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
        log.warn("Malformed multipart upload: {}", ex.getMessage());
        String message = "The upload was incomplete or malformed. Please try again.";
        return build(HttpStatus.BAD_REQUEST, message, List.of(ApiError.of("MALFORMED_UPLOAD", message)));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.debug("Authentication failure: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, MessageConstants.UNAUTHORIZED,
                List.of(ApiError.of("UNAUTHORIZED", MessageConstants.UNAUTHORIZED)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.debug("Access denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, MessageConstants.FORBIDDEN,
                List.of(ApiError.of("FORBIDDEN", MessageConstants.FORBIDDEN)));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data",
                List.of(ApiError.of("DATA_INTEGRITY_VIOLATION", "The request conflicts with existing data")));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, MessageConstants.RESOURCE_NOT_FOUND,
                List.of(ApiError.of("RESOURCE_NOT_FOUND", MessageConstants.RESOURCE_NOT_FOUND)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, MessageConstants.INTERNAL_ERROR,
                List.of(ApiError.of("INTERNAL_ERROR", MessageConstants.INTERNAL_ERROR)));
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String message, List<ApiError> errors) {
        return ResponseEntity.status(status).body(ApiResponse.error(message, status, errors));
    }
}
