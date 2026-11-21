package com.velorix.sentinel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.velorix.sentinel.config.properties.StorageProperties;
import com.velorix.sentinel.exception.BadRequestException;
import com.velorix.sentinel.exception.PayloadTooLargeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

/**
 * Unit tests for {@link UploadValidationServiceImpl}.
 * Validates Task 7.2: upload error branches, corrupted file rejection,
 * size boundaries, extension whitelist, and magic number verification.
 */
class UploadValidationServiceTest {

    private UploadValidationServiceImpl validationService;
    private StorageProperties properties;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties(
                "filesystem",
                "./data/captures",
                DataSize.ofMegabytes(10), // 10 MB max
                List.of("pcap", "pcapng"),
                List.of("application/vnd.tcpdump.pcap", "application/x-pcapng", "application/octet-stream"),
                null,
                10,
                DataSize.ofMegabytes(50));
        validationService = new UploadValidationServiceImpl(properties);
    }

    @Test
    @DisplayName("Null file upload throws BadRequestException")
    void nullFileThrowsBadRequest() {
        assertThatThrownBy(() -> validationService.validate(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Select a capture file to upload");
    }

    @Test
    @DisplayName("Empty file upload (0 bytes) throws BadRequestException")
    void emptyFileThrowsBadRequest() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pcap", "application/octet-stream", new byte[0]);

        assertThatThrownBy(() -> validationService.validate(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Empty files are rejected");
    }

    @Test
    @DisplayName("File exceeding maximum size throws PayloadTooLargeException")
    void oversizedFileThrowsPayloadTooLarge() {
        // MockMultipartFile with reported size exceeding 10 MB
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "big.pcap", "application/octet-stream", new byte[100]) {
            @Override
            public long getSize() {
                return 15 * 1024 * 1024L; // 15 MB
            }
        };

        assertThatThrownBy(() -> validationService.validate(oversized))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessageContaining("maximum allowed size is 10 MB");
    }

    @ParameterizedTest
    @ValueSource(strings = {"capture.exe", "capture.pdf", "capture.txt", "capture.sh", "capture"})
    @DisplayName("Unsupported file extension throws BadRequestException")
    void unsupportedExtensionThrows(String filename) {
        byte[] validPcapHeader = buildPcapHeader();
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "application/octet-stream", validPcapHeader);

        assertThatThrownBy(() -> validationService.validate(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only .pcap and .pcapng files are supported");
    }

    @Test
    @DisplayName("Unsupported MIME content type throws BadRequestException")
    void unsupportedContentTypeThrows() {
        byte[] validPcapHeader = buildPcapHeader();
        MockMultipartFile file = new MockMultipartFile(
                "file", "valid.pcap", "text/plain", validPcapHeader);

        assertThatThrownBy(() -> validationService.validate(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("is not a supported capture format");
    }

    @Test
    @DisplayName("Truncated capture (< 4 bytes) throws BadRequestException")
    void truncatedCaptureThrowsBadRequest() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "valid.pcap", "application/octet-stream", new byte[]{0x0a, 0x0d});

        assertThatThrownBy(() -> validationService.validate(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("The capture is truncated or corrupted");
    }

    @Test
    @DisplayName("Capture with unrecognized magic number throws BadRequestException")
    void invalidMagicNumberThrowsBadRequest() {
        byte[] notPcap = "This is definitely not a capture".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pcap", "application/octet-stream", notPcap);

        assertThatThrownBy(() -> validationService.validate(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("is not a valid PCAP or PCAPNG capture");
    }

    @ParameterizedTest
    @ValueSource(longs = {0xa1b2c3d4L, 0xd4c3b2a1L, 0xa1b23c4dL, 0x4d3cb2a1L, 0x0a0d0d0aL})
    @DisplayName("All valid capture signatures pass validation and return sanitized filename")
    void validCaptureSignaturesPass(long magic) {
        ByteBuffer buf = ByteBuffer.allocate(24);
        buf.putInt((int) magic);
        buf.put(new byte[20]);

        String filename = magic == 0x0a0d0d0aL ? "sample.pcapng" : "sample.pcap";
        String contentType = magic == 0x0a0d0d0aL ? "application/x-pcapng" : "application/vnd.tcpdump.pcap";

        MockMultipartFile file = new MockMultipartFile(
                "file", filename, contentType, buf.array());

        String result = validationService.validate(file);
        assertThat(result).isEqualTo(filename);
    }

    @Test
    @DisplayName("IO error reading multipart stream throws BadRequestException")
    void ioErrorReadingStreamThrowsBadRequest() {
        MockMultipartFile brokenStream = new MockMultipartFile(
                "file", "error.pcap", "application/octet-stream", new byte[10]) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("Simulated disk read error");
            }
        };

        assertThatThrownBy(() -> validationService.validate(brokenStream))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("The upload could not be read");
    }

    private byte[] buildPcapHeader() {
        ByteBuffer buf = ByteBuffer.allocate(24);
        buf.putInt(0xa1b2c3d4);
        buf.put(new byte[20]);
        return buf.array();
    }
}
