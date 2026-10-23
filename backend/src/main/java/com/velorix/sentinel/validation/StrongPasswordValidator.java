package com.velorix.sentinel.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Reusable password policy implementation for {@link StrongPassword}.
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    public static final int MIN_LENGTH = 10;
    /** Character length upper bound. */
    public static final int MAX_LENGTH = 72;
    /** BCrypt only consumes the first 72 bytes. Passwords exceeding 72 UTF-8 bytes are rejected. */
    public static final int MAX_BYTE_LENGTH = 72;

    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            return false;
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTE_LENGTH) {
            return false;
        }
        return UPPERCASE.matcher(value).find()
                && LOWERCASE.matcher(value).find()
                && DIGIT.matcher(value).find()
                && SPECIAL.matcher(value).find();
    }
}
