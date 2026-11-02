package com.velorix.sentinel.security.ratelimit;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Caches the request body in memory so that it can be read multiple times:
 * first by the rate limiting filter (to extract the target email for credential-stuffing defense),
 * and subsequently by downstream controller parameter resolvers.
 *
 * <p>Enforces a 64 KB size limit to prevent memory-DoS via oversized auth payloads.</p>
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    public static final int MAX_CACHED_BODY_BYTES = 64 * 1024; // 64 KB

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_CACHED_BODY_BYTES) {
            throw new IllegalArgumentException("Request payload exceeds maximum allowed size of 64KB for authentication endpoints");
        }
        try (InputStream is = request.getInputStream()) {
            byte[] bytes = is.readNBytes(MAX_CACHED_BODY_BYTES + 1);
            if (bytes.length > MAX_CACHED_BODY_BYTES) {
                throw new IllegalArgumentException("Request payload exceeds maximum allowed size of 64KB for authentication endpoints");
            }
            this.cachedBody = bytes;
        }
    }

    public byte[] getCachedBody() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // Synchronous processing
            }

            @Override
            public int read() {
                return byteArrayInputStream.read();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return byteArrayInputStream.read(b, off, len);
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        String encoding = getCharacterEncoding();
        Charset charset = (encoding != null && !encoding.isBlank()) ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
}

