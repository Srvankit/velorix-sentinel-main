package com.velorix.sentinel.inspection.reader;

import com.velorix.sentinel.exception.CaptureFormatException;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Little helper around a raw {@link InputStream} that reads fixed width integers in a
 * configurable byte order and always fails loudly on truncation.
 *
 * <p>Capture containers are length-prefixed binary formats read strictly front to back,
 * which is why this is a forward-only cursor and not a random access buffer.</p>
 */
final class ByteOrderedInput implements AutoCloseable {

    private final InputStream delegate;
    private final byte[] scratch = new byte[8];
    private boolean littleEndian;
    private long position;

    ByteOrderedInput(InputStream delegate, boolean littleEndian) {
        this.delegate = delegate;
        this.littleEndian = littleEndian;
    }

    void littleEndian(boolean value) {
        this.littleEndian = value;
    }

    long position() {
        return position;
    }

    /** Reads exactly {@code length} bytes into {@code target}, or throws. */
    void readFully(byte[] target, int offset, int length) throws IOException {
        int read = 0;
        while (read < length) {
            int count = delegate.read(target, offset + read, length - read);
            if (count < 0) {
                throw new EOFException("Capture ended after %d of %d expected bytes".formatted(read, length));
            }
            read += count;
        }
        position += length;
    }

    /** Reads exactly {@code length} bytes, returning false on a clean end of stream. */
    boolean tryReadFully(byte[] target, int length) throws IOException {
        int read = delegate.read(target, 0, length);
        if (read < 0) {
            return false;
        }
        position += read;
        while (read < length) {
            int count = delegate.read(target, read, length - read);
            if (count < 0) {
                throw new EOFException("Capture ended mid-header after %d of %d bytes".formatted(read, length));
            }
            position += count;
            read += count;
        }
        return true;
    }

    int readUnsignedShort() throws IOException {
        readFully(scratch, 0, 2);
        return toUnsignedShort(scratch, 0);
    }

    long readUnsignedInt() throws IOException {
        readFully(scratch, 0, 4);
        return toUnsignedInt(scratch, 0);
    }

    /** Advances the cursor without materialising the skipped bytes. */
    void skipFully(long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long skipped = delegate.skip(remaining);
            if (skipped <= 0) {
                if (delegate.read() < 0) {
                    throw new EOFException("Capture ended while skipping %d bytes".formatted(count));
                }
                skipped = 1;
            }
            remaining -= skipped;
            position += skipped;
        }
    }

    int toUnsignedShort(byte[] source, int offset) {
        return littleEndian
                ? (source[offset] & 0xFF) | ((source[offset + 1] & 0xFF) << 8)
                : ((source[offset] & 0xFF) << 8) | (source[offset + 1] & 0xFF);
    }

    long toUnsignedInt(byte[] source, int offset) {
        long value = littleEndian
                ? (source[offset] & 0xFFL)
                        | ((source[offset + 1] & 0xFFL) << 8)
                        | ((source[offset + 2] & 0xFFL) << 16)
                        | ((source[offset + 3] & 0xFFL) << 24)
                : ((source[offset] & 0xFFL) << 24)
                        | ((source[offset + 1] & 0xFFL) << 16)
                        | ((source[offset + 2] & 0xFFL) << 8)
                        | (source[offset + 3] & 0xFFL);
        return value & 0xFFFF_FFFFL;
    }

    /** Guards a length field before it is used to size or skip a buffer. */
    static int requireSaneLength(long value, long limit, String what) {
        if (value < 0 || value > limit) {
            throw new CaptureFormatException(
                    "%s of %d bytes is outside the accepted range (0-%d); the capture is corrupted"
                            .formatted(what, value, limit));
        }
        return (int) value;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
