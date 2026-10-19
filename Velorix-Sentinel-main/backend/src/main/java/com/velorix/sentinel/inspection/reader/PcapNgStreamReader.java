package com.velorix.sentinel.inspection.reader;

import com.velorix.sentinel.exception.CaptureFormatException;
import com.velorix.sentinel.inspection.CaptureFrameHandler;
import com.velorix.sentinel.inspection.CapturedFrame;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Streaming reader for the block based pcapng container ({@code .pcapng}).
 *
 * <p>A pcapng file is a sequence of self describing blocks. Only the blocks that carry
 * frames or the metadata needed to interpret them are decoded - Enhanced Packet Block,
 * Simple Packet Block and Interface Description Block - everything else (name resolution,
 * statistics, custom vendor blocks) is skipped by its declared length.</p>
 *
 * <p>Timestamps are per-interface: each interface declares its own resolution through the
 * {@code if_tsresol} option, so the reader keeps one divisor per interface rather than
 * assuming microseconds.</p>
 */
@Component
public class PcapNgStreamReader implements CaptureStreamReader {

    /** Section Header Block; doubles as the file magic. */
    static final long BLOCK_SECTION_HEADER = 0x0A0D0D0AL;
    static final long BLOCK_INTERFACE_DESCRIPTION = 0x00000001L;
    static final long BLOCK_SIMPLE_PACKET = 0x00000003L;
    static final long BLOCK_ENHANCED_PACKET = 0x00000006L;

    /** Byte-order magic inside the Section Header Block body. */
    private static final long BYTE_ORDER_MAGIC = 0x1A2B3C4DL;
    private static final int BLOCK_HEADER_BYTES = 8;
    private static final int BLOCK_TRAILER_BYTES = 4;
    private static final int ENHANCED_HEADER_BYTES = 20;
    private static final long MAX_BLOCK_BYTES = 16_777_216L;
    private static final long MAX_FRAME_BYTES = 262_144L;
    private static final int OPTION_END = 0;
    private static final int OPTION_IF_TSRESOL = 9;
    private static final long DEFAULT_TIMESTAMP_DIVISOR = 1_000_000L;
    private static final int DEFAULT_LINK_TYPE = 1;

    private static final Logger log = LoggerFactory.getLogger(PcapNgStreamReader.class);

    @Override
    public boolean supports(byte[] magic) {
        return isSectionHeader(magic);
    }

    @Override
    public long stream(InputStream input, CaptureFrameHandler handler) throws IOException {
        ByteOrderedInput cursor = new ByteOrderedInput(input, true);
        List<Interface> interfaces = new ArrayList<>();
        byte[] blockHeader = new byte[BLOCK_HEADER_BYTES];
        byte[] body = new byte[8192];
        long number = 0;
        boolean sectionSeen = false;

        while (cursor.tryReadFully(blockHeader, BLOCK_HEADER_BYTES)) {
            if (isSectionHeader(blockHeader)) {
                // A new section resets the byte order and the interface table.
                skipSectionHeader(cursor, blockHeader);
                interfaces.clear();
                sectionSeen = true;
                continue;
            }
            if (!sectionSeen) {
                throw new CaptureFormatException("The pcapng capture does not start with a section header block");
            }

            long blockType = cursor.toUnsignedInt(blockHeader, 0);
            int bodyLength = ByteOrderedInput.requireSaneLength(
                    cursor.toUnsignedInt(blockHeader, 4) - BLOCK_HEADER_BYTES - BLOCK_TRAILER_BYTES,
                    MAX_BLOCK_BYTES,
                    "Block length");

            if (blockType != BLOCK_ENHANCED_PACKET
                    && blockType != BLOCK_SIMPLE_PACKET
                    && blockType != BLOCK_INTERFACE_DESCRIPTION) {
                cursor.skipFully(bodyLength + (long) BLOCK_TRAILER_BYTES);
                continue;
            }

            if (body.length < bodyLength) {
                body = new byte[Math.max(bodyLength, body.length * 2)];
            }
            cursor.readFully(body, 0, bodyLength);
            cursor.skipFully(BLOCK_TRAILER_BYTES);

            if (blockType == BLOCK_INTERFACE_DESCRIPTION) {
                interfaces.add(readInterface(cursor, body, bodyLength));
                continue;
            }

            number++;
            CapturedFrame frame = blockType == BLOCK_ENHANCED_PACKET
                    ? enhancedFrame(cursor, body, bodyLength, interfaces, number)
                    : simpleFrame(cursor, body, bodyLength, interfaces, number);
            if (!handler.onFrame(frame)) {
                return number;
            }
        }

        if (!sectionSeen) {
            throw new CaptureFormatException("The pcapng capture contains no section header block");
        }
        return number;
    }

    /**
     * Consumes a Section Header Block, adopting the byte order it declares. The block
     * length itself is written in that same order, so it can only be trusted after the
     * byte-order magic has been read.
     */
    private void skipSectionHeader(ByteOrderedInput cursor, byte[] blockHeader) throws IOException {
        byte[] magic = new byte[4];
        cursor.readFully(magic, 0, 4);

        long asLittleEndian = (magic[0] & 0xFFL)
                | ((magic[1] & 0xFFL) << 8)
                | ((magic[2] & 0xFFL) << 16)
                | ((magic[3] & 0xFFL) << 24);
        boolean littleEndian = asLittleEndian == BYTE_ORDER_MAGIC;
        cursor.littleEndian(littleEndian);

        long totalLength = cursor.toUnsignedInt(blockHeader, 4);
        log.debug("pcapng section header (littleEndian={}, blockLength={})", littleEndian, totalLength);

        // Header (8) + byte-order magic (4) are already consumed.
        int remaining = ByteOrderedInput.requireSaneLength(
                totalLength - BLOCK_HEADER_BYTES - 4, MAX_BLOCK_BYTES, "Section header block length");
        cursor.skipFully(remaining);
    }

    private Interface readInterface(ByteOrderedInput cursor, byte[] body, int bodyLength) {
        if (bodyLength < 8) {
            throw new CaptureFormatException("Interface description block is shorter than its own header");
        }
        int linkType = cursor.toUnsignedShort(body, 0);
        long divisor = DEFAULT_TIMESTAMP_DIVISOR;

        // Options follow linkType(2) + reserved(2) + snaplen(4).
        int offset = 8;
        while (offset + 4 <= bodyLength) {
            int code = cursor.toUnsignedShort(body, offset);
            int length = cursor.toUnsignedShort(body, offset + 2);
            offset += 4;
            if (code == OPTION_END) {
                break;
            }
            if (code == OPTION_IF_TSRESOL && length >= 1 && offset < bodyLength) {
                divisor = timestampDivisor(body[offset]);
            }
            offset += length + ((4 - (length % 4)) % 4);
        }
        return new Interface(linkType, divisor);
    }

    /** The high bit selects a base-2 exponent; otherwise the value is a base-10 exponent. */
    private long timestampDivisor(byte resolution) {
        int exponent = resolution & 0x7F;
        if ((resolution & 0x80) != 0) {
            return exponent >= 62 ? DEFAULT_TIMESTAMP_DIVISOR : 1L << exponent;
        }
        long divisor = 1L;
        for (int i = 0; i < Math.min(exponent, 18); i++) {
            divisor *= 10L;
        }
        return divisor;
    }

    private CapturedFrame enhancedFrame(
            ByteOrderedInput cursor, byte[] body, int bodyLength, List<Interface> interfaces, long number) {

        if (bodyLength < ENHANCED_HEADER_BYTES) {
            throw new CaptureFormatException("Enhanced packet block is shorter than its own header");
        }
        int interfaceId = (int) cursor.toUnsignedInt(body, 0);
        long high = cursor.toUnsignedInt(body, 4);
        long low = cursor.toUnsignedInt(body, 8);
        int capturedLength = ByteOrderedInput.requireSaneLength(
                cursor.toUnsignedInt(body, 12),
                Math.min(MAX_FRAME_BYTES, bodyLength - (long) ENHANCED_HEADER_BYTES),
                "Captured frame length");
        int originalLength = ByteOrderedInput.requireSaneLength(
                cursor.toUnsignedInt(body, 16), MAX_FRAME_BYTES, "Original frame length");

        Interface source = interfaceAt(interfaces, interfaceId);
        return new CapturedFrame(
                number,
                toInstant((high << 32) | low, source.timestampDivisor()),
                body,
                ENHANCED_HEADER_BYTES,
                capturedLength,
                Math.max(originalLength, capturedLength),
                source.linkType());
    }

    private CapturedFrame simpleFrame(
            ByteOrderedInput cursor, byte[] body, int bodyLength, List<Interface> interfaces, long number) {

        if (bodyLength < 4) {
            throw new CaptureFormatException("Simple packet block is shorter than its own header");
        }
        int originalLength = ByteOrderedInput.requireSaneLength(
                cursor.toUnsignedInt(body, 0), MAX_FRAME_BYTES, "Original frame length");
        int capturedLength = Math.min(originalLength, bodyLength - 4);

        // Simple packet blocks carry no timestamp and no interface reference at all.
        return new CapturedFrame(
                number,
                Instant.EPOCH,
                body,
                4,
                capturedLength,
                originalLength,
                interfaceAt(interfaces, 0).linkType());
    }

    private Interface interfaceAt(List<Interface> interfaces, int index) {
        if (index >= 0 && index < interfaces.size()) {
            return interfaces.get(index);
        }
        // A packet block referencing an undeclared interface is tolerated: assume Ethernet.
        return new Interface(DEFAULT_LINK_TYPE, DEFAULT_TIMESTAMP_DIVISOR);
    }

    private Instant toInstant(long ticks, long divisor) {
        if (divisor <= 0) {
            return Instant.EPOCH;
        }
        long seconds = Long.divideUnsigned(ticks, divisor);
        long remainder = Long.remainderUnsigned(ticks, divisor);
        long nanos = divisor == 1_000_000_000L ? remainder : (remainder * 1_000_000_000L) / divisor;
        return Instant.ofEpochSecond(seconds, nanos);
    }

    private static boolean isSectionHeader(byte[] header) {
        return (header[0] & 0xFF) == 0x0A
                && (header[1] & 0xFF) == 0x0D
                && (header[2] & 0xFF) == 0x0D
                && (header[3] & 0xFF) == 0x0A;
    }

    /** Per-interface decoding context declared by an Interface Description Block. */
    private record Interface(int linkType, long timestampDivisor) {
    }
}
