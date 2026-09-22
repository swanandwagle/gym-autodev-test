package com.studio.booking.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * RFC 9457 Problem Details extended with {@code code}, {@code traceId}, and {@code errors}.
 *
 * {@code errors} is only present on 422 responses. It is omitted entirely (not null, not empty)
 * on all other status codes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorEnvelope(
        String type,
        String title,
        int status,
        String code,
        String detail,
        String instance,
        Instant timestamp,
        String traceId,
        List<FieldError> errors
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String type;
        private String title;
        private int status;
        private String code;
        private String detail;
        private String instance;
        private Instant timestamp;
        private String traceId;
        private List<FieldError> errors;

        public Builder type(String type) { this.type = type; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder status(int status) { this.status = status; return this; }
        public Builder code(String code) { this.code = code; return this; }
        public Builder detail(String detail) { this.detail = detail; return this; }
        public Builder instance(String instance) { this.instance = instance; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder errors(List<FieldError> errors) { this.errors = errors; return this; }

        public ErrorEnvelope build() {
            return new ErrorEnvelope(type, title, status, code, detail, instance, timestamp, traceId, errors);
        }
    }
}
