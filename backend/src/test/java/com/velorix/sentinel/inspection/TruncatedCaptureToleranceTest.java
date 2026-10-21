package com.velorix.sentinel.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.velorix.sentinel.dto.analysis.AnalysisResponse;
import com.velorix.sentinel.entity.Analysis;
import com.velorix.sentinel.entity.AnalysisSummary;
import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.AnalysisStatus;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.UploadStatus;
import com.velorix.sentinel.exception.CaptureFormatException;
import com.velorix.sentinel.inspection.reader.CaptureReadResult;
import com.velorix.sentinel.inspection.reader.CaptureReaderResolver;
import com.velorix.sentinel.inspection.reader.PcapNgStreamReader;
import com.velorix.sentinel.inspection.reader.PcapStreamReader;
import com.velorix.sentinel.repository.AnalysisRepository;
import com.velorix.sentinel.repository.AnalysisSummaryRepository;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.security.UserPrincipal;
import com.velorix.sentinel.service.InspectionService;
import com.velorix.sentinel.storage.FileStorageService;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Validates Task 1.4: Resilient Packet Parsing & Truncated Capture Tolerance.
 */
@SpringBootTest
@ActiveProfiles("test")
class TruncatedCaptureToleranceTest {

    @Autowired
    private PcapStreamReader pcapStreamReader;

    @Autowired
    private PcapNgStreamReader pcapNgStreamReader;

    @Autowired
    private CaptureReaderResolver readerResolver;

    @Autowired
    private InspectionEngine inspectionEngine;

    @Autowired
    private InspectionService inspectionService;

    @Autowired
    private AnalysisRepository analysisRepository;

    @Autowired
    private AnalysisSummaryRepository analysisSummaryRepository;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileStorageService fileStorageService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .firstName("Truncation")
                .lastName("Tester")
                .email("truncation." + System.nanoTime() + "@velorix.io")
                .password("hash1234567890")
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build());

        UserPrincipal principal = UserPrincipal.from(testUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /* =========================================================================
     * 1. Direct PCAP Reader Unit Tests
     * ========================================================================= */

    @Test
    @DisplayName("Normal complete PCAP parses all frames without truncation")
    void normalPcapParsesWithoutTruncation() throws Exception {
        byte[] pcapBytes = buildPcap(3);
        List<CapturedFrame> frames = new ArrayList<>();

        CaptureReadResult result = pcapStreamReader.stream(
                new ByteArrayInputStream(pcapBytes),
                frame -> {
                    frames.add(frame);
                    return true;
                });

        assertThat(result.framesRead()).isEqualTo(3L);
        assertThat(result.truncated()).isFalse();
        assertThat(frames).hasSize(3);
        assertThat(frames.get(0).number()).isEqualTo(1L);
        assertThat(frames.get(2).number()).isEqualTo(3L);
    }

    @Test
    @DisplayName("PCAP with multiple valid frames and truncated final packet body preserves valid frames")
    void pcapTruncatedAtFinalPacketBodyPreservesValidFrames() throws Exception {
        byte[] fullPcap = buildPcap(3);
        // Cut off 30 bytes from the 3rd frame's body (total frame size is 70)
        byte[] truncatedPcap = Arrays.copyOf(fullPcap, fullPcap.length - 30);
        List<CapturedFrame> frames = new ArrayList<>();

        CaptureReadResult result = pcapStreamReader.stream(
                new ByteArrayInputStream(truncatedPcap),
                frame -> {
                    frames.add(frame);
                    return true;
                });

        assertThat(result.framesRead()).isEqualTo(2L);
        assertThat(result.truncated()).isTrue();
        assertThat(frames).hasSize(2);
        assertThat(frames.get(0).number()).isEqualTo(1L);
        assertThat(frames.get(1).number()).isEqualTo(2L);
    }

    @Test
    @DisplayName("PCAP truncated in global header throws CaptureFormatException")
    void pcapTruncatedGlobalHeaderFails() {
        byte[] pcapBytes = Arrays.copyOf(buildPcap(1), 16); // Less than 24 bytes

        assertThatThrownBy(() -> pcapStreamReader.stream(
                new ByteArrayInputStream(pcapBytes), frame -> true))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("global header");
    }

    @Test
    @DisplayName("PCAP truncated mid-record header throws CaptureFormatException")
    void pcapTruncatedRecordHeaderFails() {
        byte[] pcapBytes = Arrays.copyOf(buildPcap(2), 24 + 70 + 8); // 8 bytes of 2nd record header

        assertThatThrownBy(() -> pcapStreamReader.stream(
                new ByteArrayInputStream(pcapBytes), frame -> true))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("packet header");
    }

    @Test
    @DisplayName("PCAP with 0 valid frames truncated during first frame body throws CaptureFormatException")
    void pcapTruncatedDuringFirstPacketFails() {
        byte[] pcapBytes = Arrays.copyOf(buildPcap(1), 50); // 24 + 16 + 10 bytes into 1st body

        assertThatThrownBy(() -> pcapStreamReader.stream(
                new ByteArrayInputStream(pcapBytes), frame -> true))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("no valid frames decoded");
    }

    /* =========================================================================
     * 2. Direct PCAPNG Reader Unit Tests
     * ========================================================================= */

    @Test
    @DisplayName("Normal complete PCAPNG parses all frames without truncation")
    void normalPcapNgParsesWithoutTruncation() throws Exception {
        byte[] pcapNgBytes = buildPcapNg(3);
        List<CapturedFrame> frames = new ArrayList<>();

        CaptureReadResult result = pcapNgStreamReader.stream(
                new ByteArrayInputStream(pcapNgBytes),
                frame -> {
                    frames.add(frame);
                    return true;
                });

        assertThat(result.framesRead()).isEqualTo(3L);
        assertThat(result.truncated()).isFalse();
        assertThat(frames).hasSize(3);
        assertThat(frames.get(0).number()).isEqualTo(1L);
        assertThat(frames.get(2).number()).isEqualTo(3L);
    }

    @Test
    @DisplayName("PCAPNG truncated at final packet block preserves preceding valid frames")
    void pcapNgTruncatedAtFinalBlockPreservesValidFrames() throws Exception {
        byte[] fullPcapNg = buildPcapNg(3);
        // Cut off 40 bytes from the 3rd block (EPB size is 88)
        byte[] truncatedPcapNg = Arrays.copyOf(fullPcapNg, fullPcapNg.length - 40);
        List<CapturedFrame> frames = new ArrayList<>();

        CaptureReadResult result = pcapNgStreamReader.stream(
                new ByteArrayInputStream(truncatedPcapNg),
                frame -> {
                    frames.add(frame);
                    return true;
                });

        assertThat(result.framesRead()).isEqualTo(2L);
        assertThat(result.truncated()).isTrue();
        assertThat(frames).hasSize(2);
        assertThat(frames.get(0).number()).isEqualTo(1L);
        assertThat(frames.get(1).number()).isEqualTo(2L);
    }

    @Test
    @DisplayName("PCAPNG truncated in section header block throws CaptureFormatException")
    void pcapNgTruncatedSectionHeaderFails() {
        byte[] pcapNgBytes = Arrays.copyOf(buildPcapNg(1), 16); // Truncated section header

        assertThatThrownBy(() -> pcapNgStreamReader.stream(
                new ByteArrayInputStream(pcapNgBytes), frame -> true))
                .isInstanceOf(CaptureFormatException.class);
    }

    @Test
    @DisplayName("PCAPNG with invalid block length throws CaptureFormatException")
    void pcapNgInvalidBlockLengthFails() {
        byte[] pcapNgBytes = buildPcapNg(1);
        // Corrupt EPB block length (offset 48 + 4 = 52) to an invalid value (e.g. 5, less than 12)
        pcapNgBytes[52] = 0x00;
        pcapNgBytes[53] = 0x00;
        pcapNgBytes[54] = 0x00;
        pcapNgBytes[55] = 0x05;

        assertThatThrownBy(() -> pcapNgStreamReader.stream(
                new ByteArrayInputStream(pcapNgBytes), frame -> true))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    @DisplayName("PCAPNG with 0 valid frames truncated during first packet block throws CaptureFormatException")
    void pcapNgTruncatedDuringFirstPacketFails() {
        byte[] pcapNgBytes = Arrays.copyOf(buildPcapNg(1), 60); // 28 + 20 + 12 bytes into 1st block

        assertThatThrownBy(() -> pcapNgStreamReader.stream(
                new ByteArrayInputStream(pcapNgBytes), frame -> true))
                .isInstanceOf(CaptureFormatException.class)
                .hasMessageContaining("no valid frames decoded");
    }

    /* =========================================================================
     * 3. Inspection Pipeline Integration Tests
     * ========================================================================= */

    @Test
    @DisplayName("Pipeline end-to-end: truncated PCAP completes, marks truncated, runs detection, and sets counters")
    void truncatedPcapCompletesAndRunsDetection() throws Exception {
        byte[] fullPcap = buildPcap(3);
        byte[] truncatedPcap = Arrays.copyOf(fullPcap, fullPcap.length - 30);

        String storageKey = UUID.randomUUID() + ".pcap";
        fileStorageService.store(storageKey, new ByteArrayInputStream(truncatedPcap), truncatedPcap.length);

        UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
                .user(testUser)
                .originalFileName("interrupted-capture.pcap")
                .storedFileName(storageKey)
                .fileSize((long) truncatedPcap.length)
                .checksum("chk-" + System.nanoTime())
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .build());

        AnalysisResponse queued = inspectionService.start(file.getId());
        UUID analysisId = queued.id();

        Analysis completed = awaitTerminal(analysisId);
        assertThat(completed.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(completed.isTruncated()).isTrue();
        assertThat(completed.getTotalPackets()).isEqualTo(2L);
        assertThat(completed.getProcessedPackets()).isEqualTo(2L);
        assertThat(completed.getSafePackets()).isEqualTo(2L);
        assertThat(completed.getMaliciousPackets()).isEqualTo(0L);

        // Verify automated detection executed on the 2 recovered packets
        Optional<AnalysisSummary> summaryOpt = analysisSummaryRepository.findByAnalysisId(analysisId);
        assertThat(summaryOpt).isPresent();
        assertThat(summaryOpt.get().getRiskScore()).isEqualTo(0);
    }

    @Test
    @DisplayName("Pipeline end-to-end: truncated PCAPNG completes, marks truncated, runs detection")
    void truncatedPcapNgCompletesAndRunsDetection() throws Exception {
        byte[] fullPcapNg = buildPcapNg(3);
        byte[] truncatedPcapNg = Arrays.copyOf(fullPcapNg, fullPcapNg.length - 40);

        String storageKey = UUID.randomUUID() + ".pcapng";
        fileStorageService.store(storageKey, new ByteArrayInputStream(truncatedPcapNg), truncatedPcapNg.length);

        UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
                .user(testUser)
                .originalFileName("interrupted-capture.pcapng")
                .storedFileName(storageKey)
                .fileSize((long) truncatedPcapNg.length)
                .checksum("chk-" + System.nanoTime())
                .fileType("application/x-pcapng")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .build());

        AnalysisResponse queued = inspectionService.start(file.getId());
        UUID analysisId = queued.id();

        Analysis completed = awaitTerminal(analysisId);
        assertThat(completed.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(completed.isTruncated()).isTrue();
        assertThat(completed.getTotalPackets()).isEqualTo(2L);
        assertThat(completed.getProcessedPackets()).isEqualTo(2L);

        Optional<AnalysisSummary> summaryOpt = analysisSummaryRepository.findByAnalysisId(analysisId);
        assertThat(summaryOpt).isPresent();
    }

    @Test
    @DisplayName("Pipeline end-to-end: capture with 0 valid frames fails inspection")
    void captureWithZeroValidFramesFails() throws Exception {
        // PCAP with only global header (24 bytes) and zero packet frames
        byte[] emptyPcap = Arrays.copyOf(buildPcap(1), 24);

        String storageKey = UUID.randomUUID() + ".pcap";
        fileStorageService.store(storageKey, new ByteArrayInputStream(emptyPcap), emptyPcap.length);

        UploadedFile file = uploadedFileRepository.save(UploadedFile.builder()
                .user(testUser)
                .originalFileName("empty.pcap")
                .storedFileName(storageKey)
                .fileSize((long) emptyPcap.length)
                .checksum("chk-" + System.nanoTime())
                .fileType("application/vnd.tcpdump.pcap")
                .uploadStatus(UploadStatus.READY_FOR_ANALYSIS)
                .build());

        AnalysisResponse queued = inspectionService.start(file.getId());
        UUID analysisId = queued.id();

        Analysis terminal = awaitTerminal(analysisId);
        assertThat(terminal.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(terminal.getFailureReason()).contains("no valid frames");
    }

    /* =========================================================================
     * Helpers
     * ========================================================================= */

    private Analysis awaitTerminal(UUID analysisId) throws Exception {
        Instant deadline = Instant.now().plusSeconds(5);
        while (Instant.now().isBefore(deadline)) {
            Optional<Analysis> opt = analysisRepository.findById(analysisId);
            if (opt.isPresent() && opt.get().getStatus().isTerminal()) {
                return opt.get();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Analysis " + analysisId + " did not reach terminal state within 5 seconds");
    }

    private byte[] buildPcap(int packetCount) {
        int packetSize = 16 + 54;
        ByteBuffer buf = ByteBuffer.allocate(24 + packetCount * packetSize);

        // PCAP Global Header (24 bytes)
        buf.putInt(0xa1b2c3d4);          // Magic
        buf.putShort((short) 2);         // Major
        buf.putShort((short) 4);         // Minor
        buf.putInt(0);                   // Thiszone
        buf.putInt(0);                   // Sigfigs
        buf.putInt(65535);               // Snaplen
        buf.putInt(1);                   // LinkType = LINKTYPE_ETHERNET

        for (int i = 1; i <= packetCount; i++) {
            // Record Header (16 bytes)
            buf.putInt(1703980000 + i);      // ts_sec
            buf.putInt(0);                   // ts_usec
            buf.putInt(54);                  // incl_len
            buf.putInt(54);                  // orig_len

            // Ethernet (14 bytes)
            buf.put(new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55});
            buf.put(new byte[]{0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb});
            buf.putShort((short) 0x0800);    // IPv4

            // IPv4 Header (20 bytes)
            buf.put((byte) 0x45);            // Ver/IHL
            buf.put((byte) 0x00);            // DSCP
            buf.putShort((short) 40);        // Total Length
            buf.putShort((short) (0x1000 + i)); // Identification
            buf.putShort((short) 0x4000);    // Flags / Fragment
            buf.put((byte) 64);              // TTL
            buf.put((byte) 6);               // Protocol: TCP
            buf.putShort((short) 0);         // Checksum
            buf.put(new byte[]{10, 0, 0, (byte) i}); // Src IP
            buf.put(new byte[]{10, 0, 0, 99});       // Dst IP

            // TCP Header (20 bytes)
            buf.putShort((short) (40000 + i)); // Src Port
            buf.putShort((short) 80);          // Dst Port
            buf.putInt(i);                     // Seq
            buf.putInt(0);                     // Ack
            buf.putShort((short) 0x5002);      // Offset = 5, Flags = SYN
            buf.putShort((short) 4096);        // Window
            buf.putShort((short) 0);           // Checksum
            buf.putShort((short) 0);           // Urgent pointer
        }
        return buf.array();
    }

    private byte[] buildPcapNg(int packetCount) {
        int epbSize = 88;
        ByteBuffer buf = ByteBuffer.allocate(28 + 20 + packetCount * epbSize);

        // Section Header Block (SHB) - 28 bytes
        buf.putInt(0x0A0D0D0A);         // Block Type
        buf.putInt(28);                 // Total Length
        buf.putInt(0x1A2B3C4D);         // Byte Order Magic
        buf.putShort((short) 1);        // Major Version
        buf.putShort((short) 0);        // Minor Version
        buf.putLong(-1L);               // Section Length
        buf.putInt(28);                 // Total Length trailer

        // Interface Description Block (IDB) - 20 bytes
        buf.putInt(0x00000001);         // Block Type
        buf.putInt(20);                 // Total Length
        buf.putShort((short) 1);        // LinkType = LINKTYPE_ETHERNET
        buf.putShort((short) 0);        // Reserved
        buf.putInt(65535);              // SnapLen
        buf.putInt(20);                 // Total Length trailer

        for (int i = 1; i <= packetCount; i++) {
            // Enhanced Packet Block (EPB) - 88 bytes
            buf.putInt(0x00000006);     // Block Type
            buf.putInt(epbSize);        // Total Length
            buf.putInt(0);              // Interface ID
            buf.putInt(0);              // Timestamp High
            buf.putInt(i * 1_000_000);  // Timestamp Low
            buf.putInt(54);             // Captured Length
            buf.putInt(54);             // Original Length

            // Ethernet (14 bytes)
            buf.put(new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55});
            buf.put(new byte[]{0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb});
            buf.putShort((short) 0x0800);

            // IPv4 (20 bytes)
            buf.put((byte) 0x45);
            buf.put((byte) 0x00);
            buf.putShort((short) 40);
            buf.putShort((short) (0x2000 + i));
            buf.putShort((short) 0x4000);
            buf.put((byte) 64);
            buf.put((byte) 6);
            buf.putShort((short) 0);
            buf.put(new byte[]{10, 0, 1, (byte) i});
            buf.put(new byte[]{10, 0, 1, 99});

            // TCP (20 bytes)
            buf.putShort((short) (50000 + i));
            buf.putShort((short) 443);
            buf.putInt(i);
            buf.putInt(0);
            buf.putShort((short) 0x5002);
            buf.putShort((short) 8192);
            buf.putShort((short) 0);
            buf.putShort((short) 0);

            // Padding (2 bytes)
            buf.put((byte) 0);
            buf.put((byte) 0);

            // Total Length trailer (4 bytes)
            buf.putInt(epbSize);
        }

        return buf.array();
    }
}
