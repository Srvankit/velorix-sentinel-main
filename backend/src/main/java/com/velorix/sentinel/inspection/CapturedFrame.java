package com.velorix.sentinel.inspection;

import java.time.Instant;

/**
 * One raw frame lifted off a capture stream, before any protocol decoding.
 *
 * <p>{@code data} is a buffer owned by the reader and reused between frames: consumers
 * must decode it inside the callback and must never retain the array. Passing an offset
 * instead of a fresh copy is what keeps memory flat regardless of capture size.</p>
 *
 * @param number         1-based ordinal inside the capture
 * @param timestamp      capture timestamp of the frame
 * @param data           backing buffer holding the frame bytes
 * @param offset         index of the first frame byte inside {@code data}
 * @param capturedLength number of bytes actually stored in the capture
 * @param originalLength length of the frame as seen on the wire
 * @param linkType       libpcap link layer type of the interface that recorded the frame
 */
public record CapturedFrame(
        long number,
        Instant timestamp,
        byte[] data,
        int offset,
        int capturedLength,
        int originalLength,
        int linkType) {

    /** True when the capture stored fewer bytes than the wire frame actually carried. */
    public boolean truncated() {
        return originalLength > capturedLength;
    }
}
