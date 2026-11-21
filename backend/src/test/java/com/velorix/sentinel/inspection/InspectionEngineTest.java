package com.velorix.sentinel.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.velorix.sentinel.config.properties.InspectionProperties;
import com.velorix.sentinel.entity.enums.AnalysisStage;
import com.velorix.sentinel.exception.CaptureFormatException;
import com.velorix.sentinel.inspection.decoder.PacketDecoder;
import com.velorix.sentinel.inspection.reader.CaptureReaderResolver;
import com.velorix.sentinel.inspection.reader.PcapNgStreamReader;
import com.velorix.sentinel.inspection.reader.PcapStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InspectionEngine}.
 * Validates Task 7.2: frame bounding, progress snapshots, EOF/empty handling,
 * error containment, and batch flushing without database reliance.
 */
class InspectionEngineTest {

    private CaptureReaderResolver readerResolver;
    private PacketDecoder packetDecoder;
    private PacketBatchWriter batchWriter;

    @BeforeEach
    void setUp() {
        readerResolver = new CaptureReaderResolver(List.of(new PcapStreamReader(), new PcapNgStreamReader()));
        packetDecoder = new PacketDecoder();
        batchWriter = mock(PacketBatchWriter.class);
    }

    @Test
    @DisplayName("Normal inspection streams frames, decodes packets, flushes batch, and completes at SAVING stage")
    void normalInspectionCompletesAccurately() {
        InspectionProperties properties = new InspectionProperties(
                "dpi-test", 100, 1000L, Duration.ofMinutes(1), 50, 1, 10);
        InspectionEngine engine = new InspectionEngine(readerResolver, packetDecoder, batchWriter, properties);

        byte[] pcapBytes = buildPcap(3);
        List<InspectionProgress> progressList = new ArrayList<>();
        UUID analysisId = UUID.randomUUID();

        InspectionProgress finalSnapshot = engine.inspect(
                analysisId,
                new ByteArrayInputStream(pcapBytes),
                pcapBytes.length,
                progressList::add);

        assertThat(finalSnapshot.processedPackets()).isEqualTo(3L);
        assertThat(finalSnapshot.malformedPackets()).isEqualTo(0L);
        assertThat(finalSnapshot.truncated()).isFalse();
        assertThat(finalSnapshot.stage()).isEqualTo(AnalysisStage.SAVING);

        verify(batchWriter, atLeastOnce()).write(eq(analysisId), any());
        assertThat(progressList).isNotEmpty();
    }

    @Test
    @DisplayName("Frame bounding halts reading when maxPackets limit is reached")
    void boundsFramesWhenMaxPacketsExceeded() {
        // Enforce maxPackets = 2 while capture has 5 packets
        InspectionProperties properties = new InspectionProperties(
                "dpi-test", 100, 2L, Duration.ofMinutes(1), 1, 1, 10);
        InspectionEngine engine = new InspectionEngine(readerResolver, packetDecoder, batchWriter, properties);

        byte[] pcapBytes = buildPcap(5);
        List<InspectionProgress> progressList = new ArrayList<>();
        UUID analysisId = UUID.randomUUID();

        InspectionProgress finalSnapshot = engine.inspect(
                analysisId,
                new ByteArrayInputStream(pcapBytes),
                pcapBytes.length,
                progressList::add);

        assertThat(finalSnapshot.processedPackets()).isEqualTo(2L);
        assertThat(finalSnapshot.truncated()).isTrue();
    }

    @Test
    @DisplayName("Empty capture with zero frames throws CaptureFormatException")
    void emptyCaptureThrowsCaptureFormatException() {
        InspectionProperties properties = new InspectionProperties(
                "dpi-test", 100, 1000L, Duration.ofMinutes(1), 50, 1, 10);
        InspectionEngine engine = new InspectionEngine(readerResolver, packetDecoder, batchWriter, properties);

        // Header only, zero packets
        byte[] pcapBytes = buildPcap(0);
        UUID analysisId = UUID.randomUUID();

        assertThatThrownBy(() -> engine.inspect(
                analysisId,
                new ByteArrayInputStream(pcapBytes),
                pcapBytes.length,
                progress -> {}))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("The capture contains no valid frames to inspect");
    }

    @Test
    @DisplayName("Truncated capture marks truncated in progress snapshot")
    void truncatedCaptureMarksTruncated() {
        InspectionProperties properties = new InspectionProperties(
                "dpi-test", 100, 1000L, Duration.ofMinutes(1), 50, 1, 10);
        InspectionEngine engine = new InspectionEngine(readerResolver, packetDecoder, batchWriter, properties);

        byte[] pcapBytes = buildPcap(3);
        // Truncate 20 bytes from 3rd packet body
        byte[] truncatedPcap = new byte[pcapBytes.length - 20];
        System.arraycopy(pcapBytes, 0, truncatedPcap, 0, truncatedPcap.length);
        UUID analysisId = UUID.randomUUID();

        InspectionProgress finalSnapshot = engine.inspect(
                analysisId,
                new ByteArrayInputStream(truncatedPcap),
                truncatedPcap.length,
                progress -> {});

        assertThat(finalSnapshot.processedPackets()).isEqualTo(2L);
        assertThat(finalSnapshot.truncated()).isTrue();
    }

    @Test
    @DisplayName("Progress updates are emitted at defined intervals")
    void progressEmittedAtDefinedIntervals() {
        InspectionProperties properties = new InspectionProperties(
                "dpi-test", 10, 100L, Duration.ofMinutes(1), 2, 1, 10);
        InspectionEngine engine = new InspectionEngine(readerResolver, packetDecoder, batchWriter, properties);

        byte[] pcapBytes = buildPcap(6);
        List<InspectionProgress> progressList = new ArrayList<>();
        UUID analysisId = UUID.randomUUID();

        engine.inspect(
                analysisId,
                new ByteArrayInputStream(pcapBytes),
                pcapBytes.length,
                progressList::add);

        // Progress emitted at start (READING), frame 2, frame 4, frame 6, and end (SAVING)
        assertThat(progressList.size()).isGreaterThanOrEqualTo(4);
    }

    private byte[] buildPcap(int packetCount) {
        int packetSize = 16 + 54;
        ByteBuffer buf = ByteBuffer.allocate(24 + packetCount * packetSize);

        // Global Header (24 bytes)
        buf.putInt(0xa1b2c3d4);
        buf.putShort((short) 2);
        buf.putShort((short) 4);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(65535);
        buf.putInt(1);

        for (int i = 1; i <= packetCount; i++) {
            buf.putInt(1710000000 + i);
            buf.putInt(0);
            buf.putInt(54);
            buf.putInt(54);

            // 54 bytes dummy ethernet/ipv4/tcp
            byte[] frame = new byte[54];
            frame[12] = 0x08; frame[13] = 0x00; // IPv4
            frame[14] = 0x45;
            frame[23] = 0x06; // TCP
            buf.put(frame);
        }

        return buf.array();
    }
}
