package com.velorix.sentinel.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * Raised when the binary store cannot satisfy a read or write.
 */
public class StorageException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR");
    }

    public StorageException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", cause);
    }
}
