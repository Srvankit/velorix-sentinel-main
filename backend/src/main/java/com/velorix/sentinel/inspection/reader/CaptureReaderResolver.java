package com.velorix.sentinel.inspection.reader;

import com.velorix.sentinel.exception.CaptureFormatException;
import com.velorix.sentinel.inspection.CaptureFrameHandler;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Chooses the right container reader by sniffing the first four bytes of a capture.
 *
 * <p>The file extension is never trusted: users rename {@code .pcapng} files to
 * {@code .pcap} constantly, and an attacker controls the name entirely. The magic number
 * is the only reliable signal.</p>
 */
@Component
public class CaptureReaderResolver {

    private static final Logger log = LoggerFactory.getLogger(CaptureReaderResolver.class);
    private static final int MAGIC_BYTES = 4;
    private static final int READ_BUFFER_BYTES = 64 * 1024;

    private final List<CaptureStreamReader> readers;

    public CaptureReaderResolver(List<CaptureStreamReader> readers) {
        this.readers = List.copyOf(readers);
    }

    /**
     * Streams every frame of the capture behind {@code source}.
     *
     * @param source  raw capture stream; closed by this method
     * @param handler invoked once per frame
     * @return the number of frames read
     * @throws IOException on an unrecoverable read failure
     * @throws CaptureFormatException when no reader recognises the container
     */
    public long stream(InputStream source, CaptureFrameHandler handler) throws IOException {
        try (InputStream buffered = new BufferedInputStream(source, READ_BUFFER_BYTES)) {
            buffered.mark(MAGIC_BYTES);
            byte[] magic = new byte[MAGIC_BYTES];
            int read = buffered.readNBytes(magic, 0, MAGIC_BYTES);
            if (read < MAGIC_BYTES) {
                throw new CaptureFormatException(
                        "The capture is too short to contain a valid header (%d bytes)".formatted(read));
            }
            buffered.reset();

            CaptureStreamReader reader = readers.stream()
                    .filter(candidate -> candidate.supports(magic))
                    .findFirst()
                    .orElseThrow(() -> new CaptureFormatException(
                            "Unsupported capture format; only libpcap (.pcap) and pcapng (.pcapng) files can be inspected"));

            log.debug("Capture will be read by {}", reader.getClass().getSimpleName());
            return reader.stream(buffered, handler);
        }
    }
}
