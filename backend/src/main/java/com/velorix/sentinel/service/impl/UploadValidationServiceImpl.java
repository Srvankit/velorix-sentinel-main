package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.config.properties.StorageProperties;
import com.velorix.sentinel.exception.BadRequestException;
import com.velorix.sentinel.exception.PayloadTooLargeException;
import com.velorix.sentinel.service.UploadValidationService;
import com.velorix.sentinel.util.FileNameUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Rejects anything that is not a plausible packet capture before a single byte is stored.
 */
@Service
public class UploadValidationServiceImpl implements UploadValidationService {

    private static final Logger log = LoggerFactory.getLogger(UploadValidationServiceImpl.class);

    /**
     * Leading four bytes of a well formed capture: libpcap in either endianness and both
     * timestamp resolutions, plus the pcapng Section Header Block.
     */
    private static final Set<Long> CAPTURE_MAGIC_NUMBERS = Set.of(
            0xa1b2c3d4L, 0xd4c3b2a1L, 0xa1b23c4dL, 0x4d3cb2a1L, 0x0a0d0d0aL);

    private final StorageProperties properties;

    public UploadValidationServiceImpl(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Select a capture file to upload. Empty files are rejected.");
        }

        long size = file.getSize();
        if (size <= 0L) {
            throw new BadRequestException("The selected capture is empty.");
        }
        if (size > properties.maxFileSizeBytes()) {
            throw new PayloadTooLargeException(
                    "The capture is %.1f MB. The maximum allowed size is %d MB."
                            .formatted(size / 1_048_576d, properties.maxFileSizeBytes() / 1_048_576L));
        }

        String originalName = FileNameUtils.sanitize(file.getOriginalFilename());
        String extension = FileNameUtils.extension(originalName);
        if (!properties.extensions().contains(extension)) {
            throw new BadRequestException(
                    "Only %s files are supported. '%s' was rejected."
                            .formatted(formatExtensions(), originalName));
        }

        String contentType = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT).trim();
        if (!properties.contentTypes().contains(contentType)) {
            log.warn("Rejected upload '{}' with unsupported content type '{}'", originalName, contentType);
            throw new BadRequestException(
                    "The content type '%s' is not a supported capture format.".formatted(contentType));
        }

        assertNotCorrupted(file, originalName);
        return originalName;
    }

    /**
     * Cheap corruption gate: a truncated or mislabelled file never carries a valid capture
     * magic number, so it is rejected before it reaches storage.
     */
    private void assertNotCorrupted(MultipartFile file, String originalName) {
        byte[] header = new byte[4];
        try (InputStream stream = file.getInputStream()) {
            if (stream.readNBytes(header, 0, 4) < 4) {
                throw new BadRequestException("The capture is truncated or corrupted.");
            }
        } catch (IOException ex) {
            log.warn("Could not read the header of upload '{}': {}", originalName, ex.getMessage());
            throw new BadRequestException("The upload could not be read. Please try again.");
        }

        long magic = ((long) (header[0] & 0xFF) << 24)
                | ((long) (header[1] & 0xFF) << 16)
                | ((long) (header[2] & 0xFF) << 8)
                | (header[3] & 0xFF);

        if (!CAPTURE_MAGIC_NUMBERS.contains(magic)) {
            log.warn("Rejected upload '{}' with unknown capture signature 0x{}", originalName,
                    Long.toHexString(magic));
            throw new BadRequestException(
                    "'%s' is not a valid PCAP or PCAPNG capture.".formatted(originalName));
        }
    }

    @Override
    public long maxFileSizeBytes() {
        return properties.maxFileSizeBytes();
    }

    private String formatExtensions() {
        return properties.allowedExtensions().stream().map(ext -> "." + ext).reduce(
                (left, right) -> left + " and " + right).orElse(".pcap");
    }
}
