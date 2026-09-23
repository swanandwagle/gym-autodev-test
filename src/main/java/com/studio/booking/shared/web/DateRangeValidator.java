package com.studio.booking.shared.web;

import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.error.FieldError;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates {@link DateRangeParams} against the project's date-range conventions.
 *
 * Rules:
 * <ul>
 *   <li>{@code date} is mutually exclusive with {@code from}/{@code to} → INVALID_RANGE</li>
 *   <li>{@code to} must be strictly after {@code from} → INVALID_RANGE on {@code to}</li>
 *   <li>Span {@code [from, to)} must not exceed {@code maxSpanDays} → OUT_OF_RANGE on {@code to}</li>
 * </ul>
 *
 * The {@code to} bound is exclusive: {@code from=2026-01-01, to=2026-02-01} covers 31 days.
 */
@Component
public class DateRangeValidator {

    /**
     * @param params      the date-range params to validate
     * @param maxSpanDays maximum number of days the span [from, to) may cover (inclusive count of from days)
     */
    public void validate(DateRangeParams params, int maxSpanDays) {
        List<FieldError> errors = new ArrayList<>();

        boolean hasDate = params.getDate() != null;
        boolean hasFrom = params.getFrom() != null;
        boolean hasTo   = params.getTo() != null;

        // AC-7: date is mutually exclusive with from/to
        if (hasDate && (hasFrom || hasTo)) {
            String conflictingParam = hasFrom ? "from" : "to";
            errors.add(FieldError.of(
                    "date",
                    "INVALID_RANGE",
                    "'date' cannot be combined with '" + conflictingParam + "'",
                    params.getDate() != null ? params.getDate().toString() : null));
        }

        if (!hasDate) {
            if (hasFrom && hasTo) {
                // AC-5: to must be strictly after from
                if (!params.getTo().isAfter(params.getFrom())) {
                    errors.add(FieldError.of(
                            "to",
                            "INVALID_RANGE",
                            "'to' must be strictly after 'from'",
                            params.getTo().toString()));
                } else {
                    // AC-6: span must not exceed cap
                    long spanDays = ChronoUnit.DAYS.between(params.getFrom(), params.getTo());
                    if (spanDays > maxSpanDays) {
                        errors.add(FieldError.of(
                                "to",
                                "OUT_OF_RANGE",
                                "Date span exceeds the maximum of " + maxSpanDays + " days",
                                params.getTo().toString()));
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            throw ApiException.validationFailed("Request validation failed. See errors.", errors);
        }
    }
}
