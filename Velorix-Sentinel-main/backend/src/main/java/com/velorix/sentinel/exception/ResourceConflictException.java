package com.velorix.sentinel.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

public class ResourceConflictException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceConflictException(String message) {
        super(message, HttpStatus.CONFLICT, "RESOURCE_CONFLICT");
    }
}
