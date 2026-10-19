package com.velorix.sentinel.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BadRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }
}
