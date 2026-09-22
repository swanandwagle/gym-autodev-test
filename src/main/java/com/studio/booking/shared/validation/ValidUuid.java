package com.studio.booking.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that a string is a well-formed UUID (canonical 8-4-4-4-12 hex format).
 * Used on path variables to return 422 INVALID_FORMAT instead of 500 when the
 * caller supplies a malformed ID.
 */
@Documented
@Constraint(validatedBy = ValidUuidValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUuid {

    String message() default "must be a valid UUID";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
