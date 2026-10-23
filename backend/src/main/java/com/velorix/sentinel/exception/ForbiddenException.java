package com.velorix.sentinel.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * Raised when the authenticated entity is forbidden from performing the requested action
 * (e.g. unverified email, insufficient privilege).
 */
public class ForbiddenException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    public ForbiddenException(String message, String code) {
        super(message, HttpStatus.FORBIDDEN, code);
    }
}
