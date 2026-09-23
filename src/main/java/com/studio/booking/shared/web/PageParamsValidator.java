package com.studio.booking.shared.web;

import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.error.FieldError;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates {@link PageParams} (and subclasses) against the project's pagination conventions.
 *
 * Rejects {@code page < 0}, {@code size < 1}, and {@code size > 100} with field code
 * {@code OUT_OF_RANGE} inside a {@link ErrorCode#VALIDATION_FAILED} envelope.
 * All violations are collected before throwing so callers see every problem at once.
 */
@Component
public class PageParamsValidator {

    public void validate(PageParams params) {
        List<FieldError> errors = new ArrayList<>();

        if (params.getPage() < PageParams.MIN_PAGE) {
            errors.add(FieldError.of(
                    "page",
                    "OUT_OF_RANGE",
                    "page must be >= 0",
                    params.getPage()));
        }

        if (params.getSize() < PageParams.MIN_SIZE) {
            errors.add(FieldError.of(
                    "size",
                    "OUT_OF_RANGE",
                    "size must be between " + PageParams.MIN_SIZE + " and " + PageParams.MAX_SIZE,
                    params.getSize()));
        } else if (params.getSize() > PageParams.MAX_SIZE) {
            errors.add(FieldError.of(
                    "size",
                    "OUT_OF_RANGE",
                    "size must be between " + PageParams.MIN_SIZE + " and " + PageParams.MAX_SIZE,
                    params.getSize()));
        }

        if (!errors.isEmpty()) {
            throw ApiException.validationFailed("Request validation failed. See errors.", errors);
        }
    }
}
