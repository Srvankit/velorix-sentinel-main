package com.velorix.sentinel.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * Raised when an upload exceeds the configured maximum capture size.
 */
public class PayloadTooLargeException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PayloadTooLargeException(String message) {
        super(message, HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
    }
}
