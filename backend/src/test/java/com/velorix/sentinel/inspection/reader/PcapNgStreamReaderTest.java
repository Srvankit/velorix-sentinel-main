package com.velorix.sentinel.inspection.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.velorix.sentinel.exception.CaptureFormatException;
import com.velorix.sentinel.inspection.CapturedFrame;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PcapNgStreamReader}.
 * Validates Task 7.2: Block-based PCAPNG container parsing, SHB, IDB, EPB, SPB,
 * interface resolution options, truncated capture tolerance, and structural validations.
 */
class PcapNgStreamReaderTest {

    private final PcapNgStreamReader reader = new PcapNgStreamReader();

    @Test
    @DisplayName("supports() identifies PCAPNG Section Header Block magic and rejects other formats")
    void supportsPcapNgMagic() {
        assertThat(reader.supports(new byte[]{0x0a, 0x0d, 0x0d, 0x0a})).isTrue();

        // Classic PCAP magics
        assertThat(reader.supports(new byte[]{(byte) 0xa1, (byte) 0xb2, (byte) 0xc3, (byte) 0xd4})).isFalse();
        assertThat(reader.supports(new byte[]{(byte) 0xd4, (byte) 0xc3, (byte) 0xb2, (byte) 0xa1})).isFalse();
        assertThat(reader.supports(new byte[]{0, 0, 0, 0})).isFalse();
    }

    @Test
    @DisplayName("Reads standard PCAPNG file with SHB, IDB, and EPBs accurately")
    void readsStandardPcapNgFile() throws IOException {
        byte[] pcapng = buildStandardPcapNg(3);
        List<CapturedFrame> frames = new ArrayList<>();

        CaptureReadResult result = reader.stream(new ByteArrayInputStream(pcapng), frame -> {
            frames.add(frame);
            return true;
        });

        assertThat(result.framesRead()).isEqualTo(3);
        assertThat(result.truncated()).isFalse();
        assertThat(frames).hasSize(3);
        assertThat(frames.get(0).number()).isEqualTo(1);
        assertThat(frames.get(1).number()).isEqualTo(2);
        assertThat(frames.get(2).number()).isEqualTo(3);
        assertThat(frames.get(0).linkType()).isEqualTo(1); // Ethernet from IDB
        assertThat(frames.get(0).capturedLength()).isEqualTo(54);
    }

    @Test
    @DisplayName("Reads PCAPNG file containing Simple Packet Blocks (SPB)")
    void readsPcapNgWithSimplePacketBlocks() throws IOException {
        byte[] pcapng = buildPcapNgWithSpb(2);
        List<CapturedFrame> frames = new ArrayList<>();

        CaptureReadResult result = reader.stream(new ByteArrayInputStream(pcapng), frame -> {
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
    @DisplayName("Skips unknown / non-packet blocks accurately")
    void skipsUnknownBlocks() throws IOException {
        byte[] pcapng = buildPcapNgWithCustomBlock();
        List<CapturedFrame> frames = new ArrayList<>();

        CaptureReadResult result = reader.stream(new ByteArrayInputStream(pcapng), frame -> {
            frames.add(frame);
            return true;
        });

        assertThat(result.framesRead()).isEqualTo(2);
        assertThat(result.truncated()).isFalse();
        assertThat(frames).hasSize(2);
    }

    @Test
    @DisplayName("Handler returning false stops reading further PCAPNG blocks")
    void handlerReturningFalseStopsEarly() throws IOException {
        byte[] pcapng = buildStandardPcapNg(5);
        List<CapturedFrame> frames = new ArrayList<>();

        CaptureReadResult result = reader.stream(new ByteArrayInputStream(pcapng), frame -> {
            frames.add(frame);
            return frames.size() < 2;
        });

        assertThat(frames).hasSize(2);
        assertThat(result.framesRead()).isEqualTo(2);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    @DisplayName("Empty stream throws CaptureFormatException")
    void emptyStreamThrows() {
        byte[] empty = new byte[0];

        assertThatThrownBy(() -> reader.stream(new ByteArrayInputStream(empty), frame -> true))
                .isInstanceOf(CaptureFormatException.class);
    }

    @Test
    @DisplayName("Truncation during block header throws CaptureFormatException")
    void truncatedBlockHeaderThrows() {
        byte[] truncated = new byte[4]; // Needs 8 bytes for block header

        assertThatThrownBy(() -> reader.stream(new ByteArrayInputStream(truncated), frame -> true))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("Capture truncated during block header");
    }

    @Test
    @DisplayName("Stream not starting with Section Header Block throws CaptureFormatException")
    void missingSectionHeaderThrows() {
        // Create an IDB as the first block instead of SHB
        ByteBuffer buf = ByteBuffer.allocate(20);
        buf.putInt(0x00000001); // IDB
        buf.putInt(20);
        buf.putShort((short) 1);
        buf.putShort((short) 0);
        buf.putInt(65535);
        buf.putInt(20);

        assertThatThrownBy(() -> reader.stream(new ByteArrayInputStream(buf.array()), frame -> true))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("does not start with a section header block");
    }

    @Test
    @DisplayName("Block length less than minimum or unaligned throws CaptureFormatException")
    void invalidBlockLengthThrows() {
        // Section Header Block followed by invalid block of length 6 (< 12)
        byte[] validShb = buildShb();
        ByteBuffer invalidBlock = ByteBuffer.allocate(8);
        invalidBlock.putInt(0x00000001); // IDB
        invalidBlock.putInt(6);          // Invalid length < 12

        byte[] combined = new byte[validShb.length + invalidBlock.capacity()];
        System.arraycopy(validShb, 0, combined, 0, validShb.length);
        System.arraycopy(invalidBlock.array(), 0, combined, validShb.length, invalidBlock.capacity());

        assertThatThrownBy(() -> reader.stream(new ByteArrayInputStream(combined), frame -> true))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("Block length of 6 bytes is invalid");
    }

    @Test
    @DisplayName("Truncation during first packet block body throws CaptureFormatException")
    void truncatedFirstPacketBodyThrows() {
        byte[] full = buildStandardPcapNg(1);
        // Truncate halfway through EPB body
        byte[] truncated = Arrays.copyOf(full, 28 + 20 + 30);

        assertThatThrownBy(() -> reader.stream(new ByteArrayInputStream(truncated), frame -> true))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("no valid frames decoded");
    }

    @Test
    @DisplayName("Truncation during trailing packet block after valid frames sets truncated flag")
    void truncatedTrailingPacketBodyRecovers() throws IOException {
        byte[] full = buildStandardPcapNg(3);
        // Truncate 20 bytes from last EPB
        byte[] truncated = Arrays.copyOf(full, full.length - 20);
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
    @DisplayName("Truncation while skipping trailing unknown block after valid frames sets truncated flag")
    void truncatedTrailingUnknownBlockRecovers() throws IOException {
        byte[] pcapng = buildPcapNgWithCustomBlock();
        // Truncate inside the trailing custom block
        byte[] truncated = Arrays.copyOf(pcapng, pcapng.length - 10);
        List<CapturedFrame> frames = new ArrayList<>();

        CaptureReadResult result = reader.stream(new ByteArrayInputStream(truncated), frame -> {
            frames.add(frame);
            return true;
        });

        // 1st frame is before custom block, 2nd frame was cut off
        assertThat(result.framesRead()).isEqualTo(1);
        assertThat(result.truncated()).isTrue();
    }

    // --- Helpers ---

    private byte[] buildShb() {
        ByteBuffer buf = ByteBuffer.allocate(28);
        buf.putInt(0x0A0D0D0A);         // SHB
        buf.putInt(28);
        buf.putInt(0x1A2B3C4D);         // Byte order magic
        buf.putShort((short) 1);
        buf.putShort((short) 0);
        buf.putLong(-1L);
        buf.putInt(28);
        return buf.array();
    }

    private byte[] buildStandardPcapNg(int packetCount) {
        int epbSize = 88;
        ByteBuffer buf = ByteBuffer.allocate(28 + 20 + packetCount * epbSize);

        // SHB
        buf.put(buildShb());

        // IDB
        buf.putInt(0x00000001);
        buf.putInt(20);
        buf.putShort((short) 1); // LinkType Ethernet
        buf.putShort((short) 0);
        buf.putInt(65535);
        buf.putInt(20);

        // EPBs
        for (int i = 1; i <= packetCount; i++) {
            buf.putInt(0x00000006);     // EPB
            buf.putInt(epbSize);
            buf.putInt(0);              // Interface 0
            buf.putInt(0);
            buf.putInt(i * 1_000_000);  // 1ms
            buf.putInt(54);             // Captured
            buf.putInt(54);             // Original

            byte[] frame = new byte[54];
            frame[12] = 0x08; frame[13] = 0x00; frame[14] = 0x45; frame[23] = 0x06;
            buf.put(frame);

            buf.put((byte) 0); buf.put((byte) 0); // 2 bytes padding to 32-bit align
            buf.putInt(epbSize);
        }

        return buf.array();
    }

    private byte[] buildPcapNgWithSpb(int packetCount) {
        int spbSize = 72; // 8 header + 4 origLen + 54 frame + 2 pad + 4 trailer = 72
        ByteBuffer buf = ByteBuffer.allocate(28 + 20 + packetCount * spbSize);

        buf.put(buildShb());

        // IDB
        buf.putInt(0x00000001);
        buf.putInt(20);
        buf.putShort((short) 1);
        buf.putShort((short) 0);
        buf.putInt(65535);
        buf.putInt(20);

        for (int i = 1; i <= packetCount; i++) {
            buf.putInt(0x00000003); // Simple Packet Block
            buf.putInt(spbSize);
            buf.putInt(54);         // Original length

            byte[] frame = new byte[54];
            frame[12] = 0x08; frame[13] = 0x00; frame[14] = 0x45; frame[23] = 0x06;
            buf.put(frame);

            buf.put((byte) 0); buf.put((byte) 0); // 2 bytes padding
            buf.putInt(spbSize);
        }

        return buf.array();
    }

    private byte[] buildPcapNgWithCustomBlock() {
        // Layout: SHB + IDB + EPB 1 + Custom Block (32 bytes) + EPB 2
        int epbSize = 88;
        int customBlockSize = 32;
        ByteBuffer buf = ByteBuffer.allocate(28 + 20 + epbSize + customBlockSize + epbSize);

        buf.put(buildShb());

        // IDB
        buf.putInt(0x00000001);
        buf.putInt(20);
        buf.putShort((short) 1);
        buf.putShort((short) 0);
        buf.putInt(65535);
        buf.putInt(20);

        // EPB 1
        buf.putInt(0x00000006);
        buf.putInt(epbSize);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(1_000_000);
        buf.putInt(54);
        buf.putInt(54);
        byte[] frame1 = new byte[54];
        frame1[12] = 0x08; frame1[13] = 0x00; frame1[14] = 0x45; frame1[23] = 0x06;
        buf.put(frame1);
        buf.put((byte) 0); buf.put((byte) 0);
        buf.putInt(epbSize);

        // Custom Block: type 0x00000BAD, length 32
        buf.putInt(0x00000BAD);
        buf.putInt(customBlockSize);
        buf.put(new byte[customBlockSize - 12]); // body
        buf.putInt(customBlockSize); // trailer

        // EPB 2
        buf.putInt(0x00000006);
        buf.putInt(epbSize);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(2_000_000);
        buf.putInt(54);
        buf.putInt(54);
        byte[] frame2 = new byte[54];
        frame2[12] = 0x08; frame2[13] = 0x00; frame2[14] = 0x45; frame2[23] = 0x06;
        buf.put(frame2);
        buf.put((byte) 0); buf.put((byte) 0);
        buf.putInt(epbSize);

        return buf.array();
    }
}

