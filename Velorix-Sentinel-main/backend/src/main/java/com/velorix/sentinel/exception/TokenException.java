package com.velorix.sentinel.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * Raised for any JWT problem: malformed, expired, unsupported or signature mismatch.
 */
public class TokenException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TokenException(String message) {
        this(message, null);
    }

    public TokenException(String message, Throwable cause) {
        super(message, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", cause);
    }
}
