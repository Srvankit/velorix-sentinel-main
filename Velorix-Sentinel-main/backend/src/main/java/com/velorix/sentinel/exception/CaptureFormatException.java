package com.velorix.sentinel.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * Raised when a capture cannot be parsed: bad magic number, truncated global header,
 * impossible record lengths or an unsupported container revision.
 *
 * <p>Distinct from a malformed <em>frame</em>, which is tolerated and counted; this
 * failure aborts the whole run because nothing further can be read.</p>
 */
public class CaptureFormatException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CaptureFormatException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "CAPTURE_FORMAT_INVALID");
    }

    public CaptureFormatException(String message, Throwable cause) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "CAPTURE_FORMAT_INVALID", cause);
    }
}
