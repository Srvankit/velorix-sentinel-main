package com.velorix.sentinel.inspection.reader;

import com.velorix.sentinel.exception.CaptureFormatException;
import com.velorix.sentinel.inspection.CaptureFrameHandler;
import com.velorix.sentinel.inspection.CapturedFrame;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Streaming reader for the classic libpcap container ({@code .pcap}).
 *
 * <p>Layout: a 24 byte global header followed by an unbounded sequence of
 * {@code [16 byte record header][frame bytes]} pairs. All four magic variants are
 * supported, which covers both host byte orders and both timestamp resolutions.</p>
 */
@Component
public class PcapStreamReader implements CaptureStreamReader {

    /** Big-endian, microsecond timestamps. */
    static final long MAGIC_MICROS = 0xa1b2c3d4L;
    /** Big-endian, nanosecond timestamps. */
    static final long MAGIC_NANOS = 0xa1b23c4dL;
    /** Little-endian variants of the two magics above. */
    static final long MAGIC_MICROS_SWAPPED = 0xd4c3b2a1L;
    static final long MAGIC_NANOS_SWAPPED = 0x4d3cb2a1L;

    private static final Logger log = LoggerFactory.getLogger(PcapStreamReader.class);
    private static final int GLOBAL_HEADER_BYTES = 24;
    private static final int RECORD_HEADER_BYTES = 16;
    /** No sane frame exceeds this; anything larger means the record header is garbage. */
    private static final long MAX_FRAME_BYTES = 262_144L;

    @Override
    public boolean supports(byte[] magic) {
        long value = bigEndianInt(magic);
        return value == MAGIC_MICROS
                || value == MAGIC_NANOS
                || value == MAGIC_MICROS_SWAPPED
                || value == MAGIC_NANOS_SWAPPED;
    }

    @Override
    public long stream(InputStream input, CaptureFrameHandler handler) throws IOException {
        byte[] header = new byte[GLOBAL_HEADER_BYTES];
        ByteOrderedInput cursor = new ByteOrderedInput(input, false);
        cursor.readFully(header, 0, GLOBAL_HEADER_BYTES);

        long magic = ((header[0] & 0xFFL) << 24)
                | ((header[1] & 0xFFL) << 16)
                | ((header[2] & 0xFFL) << 8)
                | (header[3] & 0xFFL);

        boolean swapped = magic == MAGIC_MICROS_SWAPPED || magic == MAGIC_NANOS_SWAPPED;
        boolean nanosecondResolution = magic == MAGIC_NANOS || magic == MAGIC_NANOS_SWAPPED;
        if (!supports(header)) {
            throw new CaptureFormatException(
                    "Unrecognised libpcap magic number 0x%08X; the file is not a valid capture".formatted(magic));
        }
        cursor.littleEndian(swapped);

        int linkType = (int) cursor.toUnsignedInt(header, 20);
        log.debug("Reading libpcap capture (linkType={}, swapped={}, nanos={})",
                linkType, swapped, nanosecondResolution);

        return readRecords(cursor, handler, linkType, nanosecondResolution);
    }

    private long readRecords(
            ByteOrderedInput cursor,
            CaptureFrameHandler handler,
            int linkType,
            boolean nanosecondResolution) throws IOException {

        byte[] recordHeader = new byte[RECORD_HEADER_BYTES];
        byte[] buffer = new byte[8192];
        long number = 0;

        while (cursor.tryReadFully(recordHeader, RECORD_HEADER_BYTES)) {
            long seconds = cursor.toUnsignedInt(recordHeader, 0);
            long fraction = cursor.toUnsignedInt(recordHeader, 4);
            int capturedLength = ByteOrderedInput.requireSaneLength(
                    cursor.toUnsignedInt(recordHeader, 8), MAX_FRAME_BYTES, "Captured frame length");
            int originalLength = ByteOrderedInput.requireSaneLength(
                    cursor.toUnsignedInt(recordHeader, 12), MAX_FRAME_BYTES, "Original frame length");

            if (buffer.length < capturedLength) {
                buffer = new byte[Integer.highestOneBit(capturedLength - 1) << 1];
            }
            cursor.readFully(buffer, 0, capturedLength);

            number++;
            Instant timestamp = Instant.ofEpochSecond(
                    seconds, nanosecondResolution ? fraction : fraction * 1_000L);

            if (!handler.onFrame(new CapturedFrame(
                    number, timestamp, buffer, 0, capturedLength,
                    Math.max(originalLength, capturedLength), linkType))) {
                break;
            }
        }
        return number;
    }

    private static long bigEndianInt(byte[] source) {
        return ((source[0] & 0xFFL) << 24)
                | ((source[1] & 0xFFL) << 16)
                | ((source[2] & 0xFFL) << 8)
                | (source[3] & 0xFFL);
    }
}
