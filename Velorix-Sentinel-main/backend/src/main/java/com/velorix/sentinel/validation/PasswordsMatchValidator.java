package com.velorix.sentinel.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Objects;
import org.springframework.beans.BeanWrapperImpl;

/**
 * Generic, reflection based implementation of {@link PasswordsMatch}.
 */
public class PasswordsMatchValidator implements ConstraintValidator<PasswordsMatch, Object> {

    private String passwordProperty;
    private String confirmationProperty;
    private String message;

    @Override
    public void initialize(PasswordsMatch annotation) {
        this.passwordProperty = annotation.password();
        this.confirmationProperty = annotation.confirmation();
        this.message = annotation.message();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        BeanWrapperImpl wrapper = new BeanWrapperImpl(value);
        Object password = wrapper.getPropertyValue(passwordProperty);
        Object confirmation = wrapper.getPropertyValue(confirmationProperty);

        if (Objects.equals(password, confirmation)) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(confirmationProperty)
                .addConstraintViolation();
        return false;
    }
}
