package com.futspring.backend.domain.auth;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * A password BCrypt can take: at most 72 bytes in UTF-8. Spring Security's encoder throws above that, in
 * {@code encode} and {@code matches} alike, so every password the API hashes or compares carries it (a 400
 * instead of a 500). {@code @Size} counts characters, and "é" is two bytes.
 */
@Documented
@Constraint(validatedBy = BcryptLength.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface BcryptLength {

    int MAX_BYTES = 72;

    String message() default "A senha é longa demais (até 72 caracteres; letras com acento contam em dobro)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<BcryptLength, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
        }
    }
}
