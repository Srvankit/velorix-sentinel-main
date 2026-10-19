package com.velorix.sentinel.inspection;

import com.velorix.sentinel.config.properties.InspectionProperties;
import com.velorix.sentinel.entity.enums.AnalysisStage;
import com.velorix.sentinel.exception.CaptureFormatException;
import com.velorix.sentinel.inspection.decoder.DecodedPacket;
import com.velorix.sentinel.inspection.decoder.PacketDecoder;
import com.velorix.sentinel.inspection.reader.CaptureReaderResolver;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The deep packet inspection pipeline: stream frames, decode them, persist them.
 *
 * <p>The engine is intentionally the only place that knows the order of the pipeline. It
 * owns no transaction and no entity - it receives a capture stream and a progress sink,
 * and reports what it saw. Persistence of the run itself is the caller's job, which keeps
 * this class unit-testable without a database.</p>
 *
 * <p>Three safety valves bound an attacker supplied capture: a maximum frame count, a
 * wall clock budget, and per-frame error containment so one damaged packet cannot abort
 * a multi-million packet run.</p>
 */
@Component
public class InspectionEngine {

    private static final Logger log = LoggerFactory.getLogger(InspectionEngine.class);

    private final CaptureReaderResolver readerResolver;
    private final PacketDecoder decoder;
    private final PacketBatchWriter writer;
    private final InspectionProperties properties;

    public InspectionEngine(
            CaptureReaderResolver readerResolver,
            PacketDecoder decoder,
            PacketBatchWriter writer,
            InspectionProperties properties) {
        this.readerResolver = readerResolver;
        this.decoder = decoder;
        this.writer = writer;
        this.properties = properties;
    }

    /**
     * Runs the full pipeline over one capture.
     *
     * @param analysisId   run the decoded frames belong to
     * @param capture      capture stream; closed by this method
     * @param expectedSize declared size of the capture in bytes, used for the percentage
     * @param onProgress   called at checkpoints so the caller can persist progress
     * @return the final progress snapshot
     * @throws CaptureFormatException when the container itself cannot be read
     */
    public InspectionProgress inspect(
            UUID analysisId, InputStream capture, long expectedSize, Consumer<InspectionProgress> onProgress) {

        Run run = new Run(analysisId, expectedSize, onProgress);
        try (capture) {
            run.emit(AnalysisStage.READING);
            readerResolver.stream(capture, run::onFrame);
            run.flush();
            run.emit(AnalysisStage.SAVING);
            log.info("Inspection {} decoded {} frames ({} malformed)",
                    analysisId, run.processed, run.malformed);
            return run.snapshot(AnalysisStage.SAVING);
        } catch (IOException ex) {
            throw new CaptureFormatException("The capture could not be read to the end: " + ex.getMessage(), ex);
        } catch (UncheckedIOException ex) {
            throw new CaptureFormatException("The capture stream failed: " + ex.getMessage(), ex);
        }
    }

    /** Per-run mutable state; one instance per capture, never shared between threads. */
    private final class Run {

        private final UUID analysisId;
        private final long expectedSize;
        private final Consumer<InspectionProgress> onProgress;
        private final List<DecodedPacket> batch;
        private final Instant deadline;
        private final long maxPackets;
        private final int batchSize;
        private final int progressInterval;

        private long processed;
        private long malformed;
        private long wireBytes;
        private long consumedBytes;
        private Instant firstFrameAt;
        private Instant lastFrameAt;
        private boolean truncated;
        private AnalysisStage stage = AnalysisStage.READING;

        private Run(UUID analysisId, long expectedSize, Consumer<InspectionProgress> onProgress) {
            this.analysisId = analysisId;
            this.expectedSize = expectedSize;
            this.onProgress = onProgress;
            this.batchSize = properties.batchSize();
            this.batch = new ArrayList<>(batchSize);
            this.maxPackets = properties.maxPackets();
            this.progressInterval = properties.progressInterval();
            this.deadline = Instant.now().plus(properties.maxDuration());
        }

        /** Handles one raw frame. Returning false stops the reader. */
        private boolean onFrame(CapturedFrame frame) {
            stage = AnalysisStage.EXTRACTING;
            DecodedPacket packet = decoder.decode(frame);

            processed++;
            if (packet.malformed()) {
                malformed++;
            }
            wireBytes += packet.packetLength();
            consumedBytes += packet.capturedLength();
            if (firstFrameAt == null) {
                firstFrameAt = packet.timestamp();
            }
            lastFrameAt = packet.timestamp();

            batch.add(packet);
            if (batch.size() >= batchSize) {
                flush();
            }
            if (processed % progressInterval == 0) {
                emit(AnalysisStage.EXTRACTING);
            }

            if (processed >= maxPackets) {
                truncated = true;
                log.warn("Inspection {} stopped at the configured ceiling of {} frames", analysisId, maxPackets);
                return false;
            }
            if (Instant.now().isAfter(deadline)) {
                truncated = true;
                log.warn("Inspection {} stopped after exceeding its {} budget", analysisId, properties.maxDuration());
                return false;
            }
            return true;
        }

        private void flush() {
            if (batch.isEmpty()) {
                return;
            }
            writer.write(analysisId, batch);
            batch.clear();
        }

        private void emit(AnalysisStage current) {
            stage = current;
            onProgress.accept(snapshot(current));
        }

        private InspectionProgress snapshot(AnalysisStage current) {
            return new InspectionProgress(
                    current == null ? stage : current,
                    processed,
                    malformed,
                    percentage(),
                    firstFrameAt,
                    lastFrameAt,
                    wireBytes,
                    truncated);
        }

        /**
         * Progress is estimated from bytes consumed, not frames: the frame total is
         * unknown until the capture ends, but the file size is known up front.
         */
        private int percentage() {
            if (expectedSize <= 0) {
                return processed == 0 ? 0 : 50;
            }
            long ratio = Math.min(100L, (consumedBytes * 100L) / expectedSize);
            return (int) Math.max(1L, ratio);
        }
    }

    /** Exposed for logging and tests: the budget a single run is allowed to take. */
    public Duration maxDuration() {
        return properties.maxDuration();
    }
}
