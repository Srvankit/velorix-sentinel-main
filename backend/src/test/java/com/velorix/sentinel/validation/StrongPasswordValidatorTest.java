package com.velorix.sentinel.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StrongPasswordValidatorTest {

    private StrongPasswordValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StrongPasswordValidator();
    }

    @Test
    @DisplayName("Valid ASCII password meeting all complexity criteria passes")
    void validStandardAsciiPasswordPasses() {
        assertThat(validator.isValid("Str0ng!Passw0rd", null)).isTrue();
        assertThat(validator.isValid("Admin#2026Secure", null)).isTrue();
    }

    @Test
    @DisplayName("Exact 72-byte ASCII password passes")
    void exact72ByteAsciiPasswordPasses() {
        // 72 characters, ASCII (1 byte per char) = 72 bytes
        String password72 = "A1!" + "a".repeat(69);
        assertThat(password72.length()).isEqualTo(72);
        assertThat(password72.getBytes(StandardCharsets.UTF_8).length).isEqualTo(72);
        assertThat(validator.isValid(password72, null)).isTrue();
    }

    @Test
    @DisplayName("73-byte ASCII password is rejected")
    void exceeds72CharsAndBytesAsciiRejected() {
        String password73 = "A1!" + "a".repeat(70);
        assertThat(password73.length()).isEqualTo(73);
        assertThat(password73.getBytes(StandardCharsets.UTF_8).length).isEqualTo(73);
        assertThat(validator.isValid(password73, null)).isFalse();
    }

    @Test
    @DisplayName("Password below minimum length (10 chars) is rejected")
    void belowMinLengthRejected() {
        assertThat(validator.isValid("A1!a", null)).isFalse();
        assertThat(validator.isValid("A1!aaaa", null)).isFalse(); // 7 chars
        assertThat(validator.isValid("A1!aaaaa", null)).isFalse(); // 8 chars
        assertThat(validator.isValid("A1!aaaaaa", null)).isFalse(); // 9 chars
        assertThat(validator.isValid("A1!aaaaaaa", null)).isTrue(); // 10 chars
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Null, empty, and blank strings are rejected")
    void nullOrBlankRejected(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    @DisplayName("Missing complexity dimensions are rejected")
    void missingComplexityDimensionsRejected() {
        assertThat(validator.isValid("lowercase1!", null)).isFalse(); // missing uppercase
        assertThat(validator.isValid("UPPERCASE1!", null)).isFalse(); // missing lowercase
        assertThat(validator.isValid("UpperLower!", null)).isFalse(); // missing digit
        assertThat(validator.isValid("UpperLower1", null)).isFalse(); // missing special char
    }

    @Test
    @DisplayName("Multibyte password at or below 72 bytes passes")
    void multibytePasswordUnderOrAt72BytesPasses() {
        // '€' is 3 UTF-8 bytes.
        // "Aa1!" is 4 ASCII characters (4 bytes).
        // 22 '€' characters = 66 bytes. Total = 70 bytes, 26 characters.
        String password70Bytes = "Aa1!" + "€".repeat(22);
        assertThat(password70Bytes.length()).isEqualTo(26);
        assertThat(password70Bytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(70);
        assertThat(validator.isValid(password70Bytes, null)).isTrue();

        // 22 '€' (66 bytes) + "Aa1!bb" (6 bytes) = 72 bytes total, 28 characters.
        String password72Bytes = "Aa1!bb" + "€".repeat(22);
        assertThat(password72Bytes.length()).isEqualTo(28);
        assertThat(password72Bytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(72);
        assertThat(validator.isValid(password72Bytes, null)).isTrue();
    }

    @Test
    @DisplayName("Multibyte password with ≤72 characters but >72 UTF-8 bytes is rejected (BCrypt truncation protection)")
    void multibytePasswordExceeding72BytesRejectedEvenWhenCharLengthUnder72() {
        // 25 '€' characters (75 bytes) + "Aa1!" (4 bytes) = 79 bytes total, but only 29 characters!
        String password79Bytes = "Aa1!" + "€".repeat(25);
        assertThat(password79Bytes.length()).isEqualTo(29);
        assertThat(password79Bytes.length()).isLessThanOrEqualTo(72);
        assertThat(password79Bytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(79);
        assertThat(password79Bytes.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(72);

        // Character length check alone would pass, but byte-length check must reject
        assertThat(validator.isValid(password79Bytes, null)).isFalse();
    }

    @Test
    @DisplayName("Multibyte password at exactly 73 UTF-8 bytes boundary is rejected")
    void multibytePasswordAt73BytesBoundaryRejected() {
        // "Aa1!b" (5 bytes) + 22 '€' (66 bytes) + 1 'é' (2 bytes) = 73 bytes total, 28 characters.
        String password73Bytes = "Aa1!b" + "€".repeat(22) + "é";
        assertThat(password73Bytes.length()).isEqualTo(28);
        assertThat(password73Bytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(73);
        assertThat(validator.isValid(password73Bytes, null)).isFalse();
    }
}
