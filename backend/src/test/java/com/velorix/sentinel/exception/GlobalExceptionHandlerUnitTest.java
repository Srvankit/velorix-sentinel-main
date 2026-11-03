package com.velorix.sentinel.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.common.ApiResponse;
import com.velorix.sentinel.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Unit tests for {@link GlobalExceptionHandler} verifying explicit handling of
 * protocol exceptions, client errors, and accurate HTTP status/error codes.
 */
@DisplayName("Task 6.3 — Global Exception Handler Unit Tests")
class GlobalExceptionHandlerUnitTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        request.setMethod("GET");
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException maps to 405 Method Not Allowed with Allow header")
    void handleMethodNotAllowed() {
        var ex = new HttpRequestMethodNotSupportedException("PUT", List.of("POST", "GET"));
        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotAllowed(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.POST, HttpMethod.GET);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().status()).isEqualTo(405);
        assertThat(response.getBody().errors()).hasSize(1);
        assertThat(response.getBody().errors().get(0).code()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupportedException maps to 415 Unsupported Media Type with Accept header")
    void handleUnsupportedMediaType() {
        var ex = new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnsupportedMediaType(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getHeaders().getAccept()).containsExactly(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().status()).isEqualTo(415);
        assertThat(response.getBody().errors().get(0).code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("HttpMediaTypeNotAcceptableException maps to 406 Not Acceptable")
    void handleNotAcceptable() {
        var ex = new HttpMediaTypeNotAcceptableException("Could not find acceptable representation");
        ResponseEntity<ApiResponse<Void>> response = handler.handleNotAcceptable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(406);
        assertThat(response.getBody().errors().get(0).code()).isEqualTo("NOT_ACCEPTABLE");
    }

    @Test
    @DisplayName("MissingServletRequestPartException maps to 400 Bad Request with field scoping")
    void handleMissingRequestPart() {
        var ex = new MissingServletRequestPartException("file");
        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingRequestPart(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().errors().get(0).code()).isEqualTo("MISSING_REQUEST_PART");
        assertThat(response.getBody().errors().get(0).field()).isEqualTo("file");
    }

    @Test
    @DisplayName("PropertyReferenceException maps to 400 Bad Request with field scoping")
    void handlePropertyReference() {
        TypeInformation<?> typeInfo = TypeInformation.of(User.class);
        var ex = new PropertyReferenceException("invalidField", typeInfo, List.of());
        ResponseEntity<ApiResponse<Void>> response = handler.handlePropertyReference(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().errors().get(0).code()).isEqualTo("INVALID_PROPERTY");
        assertThat(response.getBody().errors().get(0).field()).isEqualTo("invalidField");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException maps to 400 Bad Request with field scoping")
    void handleMethodArgumentTypeMismatch() {
        var ex = new MethodArgumentTypeMismatchException("abc", java.util.UUID.class, "id", null, null);
        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().errors().get(0).code()).isEqualTo("TYPE_MISMATCH");
        assertThat(response.getBody().errors().get(0).field()).isEqualTo("id");
    }

    @Test
    @DisplayName("MissingServletRequestParameterException maps to 400 Bad Request with field scoping")
    void handleMissingParameter() {
        var ex = new MissingServletRequestParameterException("page", "Integer");
        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingParameter(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().errors().get(0).code()).isEqualTo("MISSING_PARAMETER");
        assertThat(response.getBody().errors().get(0).field()).isEqualTo("page");
    }

    @Test
    @DisplayName("MaxUploadSizeExceededException maps to 413 Payload Too Large")
    void handleMaxUploadSizeExceeded() {
        var ex = new MaxUploadSizeExceededException(104857600L);
        ResponseEntity<ApiResponse<Void>> response = handler.handleUploadTooLarge(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(413);
        assertThat(response.getBody().errors().get(0).code()).isEqualTo("FILE_TOO_LARGE");
    }
}

