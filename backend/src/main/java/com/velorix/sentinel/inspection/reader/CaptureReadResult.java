package com.velorix.sentinel.inspection.reader;

/**
 * Result of streaming a capture container.
 *
 * @param framesRead number of complete, valid frames read and handed to the handler
 * @param truncated  true if the capture suffered a recoverable unexpected EOF at the tail
 */
public record CaptureReadResult(long framesRead, boolean truncated) {

    public static CaptureReadResult complete(long framesRead) {
        return new CaptureReadResult(framesRead, false);
    }

    public static CaptureReadResult truncated(long framesRead) {
        return new CaptureReadResult(framesRead, true);
    }
}
