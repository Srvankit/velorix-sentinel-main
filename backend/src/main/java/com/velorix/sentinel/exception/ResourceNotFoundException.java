package com.velorix.sentinel.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    public static ResourceNotFoundException of(String resource, Object identifier) {
        return new ResourceNotFoundException("%s not found for identifier '%s'".formatted(resource, identifier));
    }
}
