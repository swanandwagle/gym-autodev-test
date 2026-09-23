package com.studio.booking.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldError(
        String field,
        String code,
        String message,
        Object rejectedValue
) {
    /** Maximum length for string rejectedValues echoed back to the caller. */
    static final int MAX_REJECTED_VALUE_LENGTH = 200;

    public static FieldError of(String field, String code, String message) {
        return new FieldError(field, code, message, null);
    }

    public static FieldError of(String field, String code, String message, Object rejectedValue) {
        return new FieldError(field, code, message, truncate(rejectedValue));
    }

    private static Object truncate(Object value) {
        if (value instanceof String s && s.length() > MAX_REJECTED_VALUE_LENGTH) {
            return s.substring(0, MAX_REJECTED_VALUE_LENGTH) + "…";
        }
        return value;
    }
}
