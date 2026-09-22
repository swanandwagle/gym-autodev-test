package com.studio.booking.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldError(
        String field,
        String code,
        String message,
        Object rejectedValue
) {
    public static FieldError of(String field, String code, String message) {
        return new FieldError(field, code, message, null);
    }

    public static FieldError of(String field, String code, String message, Object rejectedValue) {
        return new FieldError(field, code, message, rejectedValue);
    }
}
