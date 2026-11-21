package com.velorix.sentinel.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link CachedBodyHttpServletRequest}.
 */
class CachedBodyHttpServletRequestTest {

    @Test
    @DisplayName("Caches request body and allows reading multiple times via stream and reader")
    void cachesBodyAndAllowsMultipleReads() throws IOException {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        String payload = "{\"email\":\"test@velorix.io\",\"password\":\"secret\"}";
        mockRequest.setContent(payload.getBytes(StandardCharsets.UTF_8));
        mockRequest.setCharacterEncoding("UTF-8");

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(mockRequest);

        assertThat(wrapped.getCachedBody()).isEqualTo(payload.getBytes(StandardCharsets.UTF_8));

        // Read via InputStream
        ServletInputStream stream = wrapped.getInputStream();
        assertThat(stream.isReady()).isTrue();
        assertThat(stream.isFinished()).isFalse();

        byte[] buffer = new byte[payload.length()];
        int bytesRead = stream.read(buffer, 0, buffer.length);
        assertThat(bytesRead).isEqualTo(payload.length());
        assertThat(new String(buffer, StandardCharsets.UTF_8)).isEqualTo(payload);
        assertThat(stream.isFinished()).isTrue();
        stream.setReadListener(null);

        // Read again via getReader()
        BufferedReader reader = wrapped.getReader();
        assertThat(reader.readLine()).isEqualTo(payload);

        // Read byte-by-byte
        ServletInputStream stream2 = wrapped.getInputStream();
        int firstByte = stream2.read();
        assertThat(firstByte).isEqualTo('{');
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when Content-Length exceeds 64KB")
    void rejectsOversizedContentLength() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return 65 * 1024L; // 65 KB
            }
        };

        assertThatThrownBy(() -> new CachedBodyHttpServletRequest(mockRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request payload exceeds maximum allowed size of 64KB");
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when actual payload stream exceeds 64KB")
    void rejectsOversizedStream() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        byte[] bigPayload = new byte[65 * 1024]; // 65 KB
        mockRequest.setContent(bigPayload);

        assertThatThrownBy(() -> new CachedBodyHttpServletRequest(mockRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request payload exceeds maximum allowed size of 64KB");
    }

    @Test
    @DisplayName("Uses UTF-8 fallback when character encoding is not set")
    void usesUtf8FallbackWhenEncodingNull() throws IOException {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setContent("hello".getBytes(StandardCharsets.UTF_8));
        mockRequest.setCharacterEncoding(null);

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(mockRequest);
        BufferedReader reader = wrapped.getReader();
        assertThat(reader.readLine()).isEqualTo("hello");
    }
}
