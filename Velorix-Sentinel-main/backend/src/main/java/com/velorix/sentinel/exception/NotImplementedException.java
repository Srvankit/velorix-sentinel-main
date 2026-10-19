package com.velorix.sentinel.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * Raised by scaffolded services whose business logic is intentionally not implemented yet.
 */
public class NotImplementedException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NotImplementedException(String capability) {
        super("%s is not implemented yet".formatted(capability), HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED");
    }
}
