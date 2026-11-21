package com.velorix.sentinel.inspection.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.velorix.sentinel.exception.CaptureFormatException;
import com.velorix.sentinel.inspection.CapturedFrame;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PcapStreamReader}.
 * Validates Task 7.2: PCAP stream reading, byte order variants, resolution, truncation tolerance,
 * and error branch containment.
 */
class PcapStreamReaderTest {

    private final PcapStreamReader reader = new PcapStreamReader();

    @Test
    @DisplayName("supports() identifies all four libpcap magic numbers and rejects others")
    void supportsAllMagicVariants() {
        // Big-endian microsecond
        assertThat(reader.supports(new byte[]{(byte) 0xa1, (byte) 0xb2, (byte) 0xc3, (byte) 0xd4})).isTrue();
        // Little-endian microsecond
        assertThat(reader.supports(new byte[]{(byte) 0xd4, (byte) 0xc3, (byte) 0xb2, (byte) 0xa1})).isTrue();
        // Big-endian nanosecond
        assertThat(reader.supports(new byte[]{(byte) 0xa1, (byte) 0xb2, (byte) 0x3c, (byte) 0x4d})).isTrue();
        // Little-endian nanosecond
        assertThat(reader.supports(new byte[]{(byte) 0x4d, (byte) 0x3c, (byte) 0xb2, (byte) 0xa1})).isTrue();

        // Rejections: PCAPNG, zeros, random, ASCII
        assertThat(reader.supports(new byte[]{0x0a, 0x0d, 0x0d, 0x0a})).isFalse();
        assertThat(reader.supports(new byte[]{0x00, 0x00, 0x00, 0x00})).isFalse();
        assertThat(reader.supports(new byte[]{0x7f, 0x45, 0x4c, 0x46})).isFalse(); // ELF
        assertThat(reader.supports(new byte[]{0x50, 0x4b, 0x03, 0x04})).isFalse(); // ZIP
    }

    @Test
    @DisplayName("Reads standard big-endian microsecond PCAP with multiple frames")
    void readsStandardBigEndianMicrosPcap() throws IOException {
        byte[] pcap = buildPcap(ByteOrder.BIG_ENDIAN, 0xa1b2c3d4, false, 3);
        List<CapturedFrame> frames = new ArrayList<>();

        CaptureReadResult result = reader.stream(new ByteArrayInputStream(pcap), frame -> {
            frames.add(frame);
            return true;
        });

        assertThat(result.framesRead()).isEqualTo(3);
        assertThat(result.truncated()).isFalse();
        assertThat(frames).hasSize(3);
        assertThat(frames.get(0).number()).isEqualTo(1);
        assertThat(frames.get(1).number()).isEqualTo(2);
        assertThat(frames.get(2).number()).isEqualTo(3);
        assertThat(frames.get(0).linkType()).isEqualTo(1); // Ethernet
        assertThat(frames.get(0).capturedLength()).isEqualTo(54);
    }

    @Test
    @DisplayName("Reads little-endian (swapped) microsecond PCAP accurately")
    void readsLittleEndianSwappedMicrosPcap() throws IOException {
        byte[] pcap = buildPcap(ByteOrder.LITTLE_ENDIAN, (int) 0xa1b2c3d4L, false, 2);
        List<CapturedFrame> frames = new ArrayList<>();

        CaptureReadResult result = reader.stream(new ByteArrayInputStream(pcap), frame -> {
            frames.add(frame);
            return true;
        });

        assertThat(result.framesRead()).isEqualTo(2);
        assertThat(result.truncated()).isFalse();
        assertThat(frames).hasSize(2);
        assertThat(frames.get(0).number()).isEqualTo(1);
        assertThat(frames.get(1).number()).isEqualTo(2);
    }

    @Test
    @DisplayName("Reads big-endian nanosecond resolution PCAP accurately")
    void readsBigEndianNanosecondPcap() throws IOException {
        byte[] pcap = buildPcap(ByteOrder.BIG_ENDIAN, (int) 0xa1b23c4dL, true, 2);
        List<CapturedFrame> frames = new ArrayList<>();

        CaptureReadResult result = reader.stream(new ByteArrayInputStream(pcap), frame -> {
            frames.add(frame);
            return true;
        });

        assertThat(result.framesRead()).isEqualTo(2);
        assertThat(result.truncated()).isFalse();
        assertThat(frames).hasSize(2);
        // Nanoseconds should map directly to nano-of-second
        assertThat(frames.get(0).timestamp().getNano()).isEqualTo(500_000_000);
    }

    @Test
    @DisplayName("Reads little-endian nanosecond resolution PCAP accurately")
    void readsLittleEndianNanosecondPcap() throws IOException {
        byte[] pcap = buildPcap(ByteOrder.LITTLE_ENDIAN, (int) 0xa1b23c4dL, true, 2);
        List<CapturedFrame> frames = new ArrayList<>();

        CaptureReadResult result = reader.stream(new ByteArrayInputStream(pcap), frame -> {
            frames.add(frame);
            return true;
        });

        assertThat(result.framesRead()).isEqualTo(2);
        assertThat(result.truncated()).isFalse();
        assertThat(frames).hasSize(2);
        assertThat(frames.get(0).timestamp().getNano()).isEqualTo(500_000_000);
    }

    @Test
    @DisplayName("Handler returning false aborts stream processing early")
    void handlerReturningFalseAbortsProcessing() throws IOException {
        byte[] pcap = buildPcap(ByteOrder.BIG_ENDIAN, 0xa1b2c3d4, false, 5);
        List<CapturedFrame> frames = new ArrayList<>();

        CaptureReadResult result = reader.stream(new ByteArrayInputStream(pcap), frame -> {
            frames.add(frame);
            return frames.size() < 2; // Stop after 2 frames
        });

        assertThat(frames).hasSize(2);
        assertThat(result.framesRead()).isEqualTo(2);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    @DisplayName("Stream truncated during global header throws CaptureFormatException")
    void truncatedGlobalHeaderThrows() {
        byte[] truncatedHeader = new byte[16]; // Expected 24 bytes

        assertThatThrownBy(() -> reader.stream(new ByteArrayInputStream(truncatedHeader), frame -> true))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("Capture truncated during global header");
    }

    @Test
    @DisplayName("Empty stream throws CaptureFormatException")
    void emptyStreamThrows() {
        byte[] empty = new byte[0];

        assertThatThrownBy(() -> reader.stream(new ByteArrayInputStream(empty), frame -> true))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("Capture truncated during global header");
    }

    @Test
    @DisplayName("Invalid magic number throws CaptureFormatException")
    void invalidMagicThrows() {
        byte[] invalidHeader = new byte[24];
        invalidHeader[0] = 0x12;
        invalidHeader[1] = 0x34;
        invalidHeader[2] = 0x56;
        invalidHeader[3] = 0x78;

        assertThatThrownBy(() -> reader.stream(new ByteArrayInputStream(invalidHeader), frame -> true))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("Unrecognised libpcap magic number");
    }

    @Test
    @DisplayName("Truncation during packet header throws CaptureFormatException")
    void truncatedRecordHeaderThrows() {
        byte[] pcap = buildPcap(ByteOrder.BIG_ENDIAN, 0xa1b2c3d4, false, 2);
        // Truncate halfway through 2nd record header (24 + 16 + 54 + 8)
        byte[] truncated = Arrays.copyOf(pcap, 24 + 16 + 54 + 8);

        assertThatThrownBy(() -> reader.stream(new ByteArrayInputStream(truncated), frame -> true))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("Capture truncated during packet header");
    }

    @Test
    @DisplayName("Truncation during first packet body throws CaptureFormatException")
    void truncatedFirstPacketBodyThrows() {
        byte[] pcap = buildPcap(ByteOrder.BIG_ENDIAN, 0xa1b2c3d4, false, 1);
        // Truncate mid-body of first packet (24 + 16 + 20)
        byte[] truncated = Arrays.copyOf(pcap, 24 + 16 + 20);

        assertThatThrownBy(() -> reader.stream(new ByteArrayInputStream(truncated), frame -> true))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("Capture truncated during the first frame body: no valid frames decoded");
    }

    @Test
    @DisplayName("Truncation during final packet body after valid frames sets truncated flag")
    void truncatedFinalPacketBodyRecoversValidFrames() throws IOException {
        byte[] pcap = buildPcap(ByteOrder.BIG_ENDIAN, 0xa1b2c3d4, false, 3);
        // Full size: 24 + 3 * (16 + 54) = 234. Truncate 20 bytes from last packet body.
        byte[] truncated = Arrays.copyOf(pcap, pcap.length - 20);
        List<CapturedFrame> frames = new ArrayList<>();

        CaptureReadResult result = reader.stream(new ByteArrayInputStream(truncated), frame -> {
            frames.add(frame);
            return true;
        });

        assertThat(result.framesRead()).isEqualTo(2);
        assertThat(result.truncated()).isTrue();
        assertThat(frames).hasSize(2);
    }

    @Test
    @DisplayName("Frame length exceeding maximum allowable throws CaptureFormatException")
    void insaneFrameLengthThrows() {
        byte[] pcap = buildPcap(ByteOrder.BIG_ENDIAN, 0xa1b2c3d4, false, 1);
        // Overwrite incl_len at offset 24 + 8 with 500,000 (> 262,144)
        ByteBuffer buf = ByteBuffer.wrap(pcap);
        buf.putInt(24 + 8, 500_000);

        assertThatThrownBy(() -> reader.stream(new ByteArrayInputStream(pcap), frame -> true))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("Captured frame length of 500000 bytes is outside the accepted range");
    }

    private byte[] buildPcap(ByteOrder order, int magic, boolean nanos, int packetCount) {
        int packetSize = 16 + 54;
        ByteBuffer buf = ByteBuffer.allocate(24 + packetCount * packetSize);
        buf.order(order);

        // Global Header (24 bytes)
        buf.putInt(magic);
        buf.putShort((short) 2);
        buf.putShort((short) 4);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(65535);
        buf.putInt(1); // LinkType Ethernet

        for (int i = 1; i <= packetCount; i++) {
            buf.putInt(1710000000 + i); // seconds
            buf.putInt(nanos ? 500_000_000 : 500_000); // fraction
            buf.putInt(54); // incl_len
            buf.putInt(54); // orig_len

            // 54 bytes dummy ethernet/ipv4/tcp payload
            byte[] frame = new byte[54];
            frame[12] = 0x08; // EtherType IPv4
            frame[13] = 0x00;
            frame[14] = 0x45; // IPv4
            frame[23] = 0x06; // TCP
            buf.put(frame);
        }

        return buf.array();
    }
}
