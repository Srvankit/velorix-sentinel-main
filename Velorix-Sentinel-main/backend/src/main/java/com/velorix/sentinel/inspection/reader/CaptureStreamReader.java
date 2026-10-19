package com.velorix.sentinel.inspection.reader;

import com.velorix.sentinel.inspection.CaptureFrameHandler;
import java.io.IOException;
import java.io.InputStream;

/**
 * Streams the frames of one capture container format.
 *
 * <p>Implementations are stateless singletons and must never buffer the whole capture:
 * they read a record header, hand the frame to the handler and move on, so a 10 GB
 * capture costs the same memory as a 10 KB one.</p>
 */
public interface CaptureStreamReader {

    /** True when this reader recognises the container behind the supplied magic bytes. */
    boolean supports(byte[] magic);

    /**
     * Streams every frame to {@code handler}.
     *
     * @param input  positioned at the very start of the capture; the caller owns closing it
     * @param handler invoked once per frame; returning {@code false} stops the stream
     * @return the number of frames handed to the handler
     * @throws IOException on an unrecoverable read failure
     */
    long stream(InputStream input, CaptureFrameHandler handler) throws IOException;
}
