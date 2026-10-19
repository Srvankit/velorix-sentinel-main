package com.velorix.sentinel.inspection;

/**
 * Callback invoked once per frame while a capture is streamed.
 */
@FunctionalInterface
public interface CaptureFrameHandler {

    /**
     * @param frame the current frame; its byte buffer is only valid for the duration of
     *              this call
     * @return {@code true} to keep reading, {@code false} to stop the stream early
     */
    boolean onFrame(CapturedFrame frame);
}
