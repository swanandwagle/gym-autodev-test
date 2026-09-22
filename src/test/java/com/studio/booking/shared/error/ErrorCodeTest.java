package com.studio.booking.shared.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for the ErrorCode catalogue (GYM-19).
 *
 * AC-1: Every code exists in the enum with a documented HTTP status.
 * AC-2: HTTP status is identical by construction — carried on the enum constant.
 * AC-3: Every code has a message template in error-messages.properties.
 * AC-5: ErrorEnvelope.code() is typed as ErrorCode — cannot accept a String.
 * AC-6: toKebab() URI derivation tested for a representative sample including multi-word codes.
 */
class ErrorCodeTest {

    // =========================================================================
    // AC-1: Every enum constant has a non-null HTTP status
    // =========================================================================

    @Test
    void testAc1AllCodesHaveDocumentedHttpStatus() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.httpStatus())
                    .as("ErrorCode.%s must have a non-null httpStatus", code.name())
                    .isNotNull();
        }
    }

    @Test
    void testAc1ExpectedCodesPresent() {
        Set<String> names = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::name)
                .collect(Collectors.toSet());

        // 400
        assertThat(names).contains("MALFORMED_REQUEST");

        // 403
        assertThat(names).contains("NOT_PERMITTED", "MEMBER_SUSPENDED", "MEMBER_INACTIVE");

        // 404
        assertThat(names).contains(
                "NOT_FOUND", "MEMBER_NOT_FOUND", "MEMBERSHIP_PLAN_NOT_FOUND",
                "MEMBERSHIP_NOT_FOUND", "CLASS_TYPE_NOT_FOUND", "INSTRUCTOR_NOT_FOUND",
                "ROOM_NOT_FOUND", "SESSION_NOT_FOUND", "BOOKING_NOT_FOUND",
                "WAITLIST_ENTRY_NOT_FOUND");

        // 409
        assertThat(names).contains(
                "CONFLICT", "CONCURRENT_MODIFICATION", "DUPLICATE_BOOKING",
                "SESSION_FULL", "SESSION_CANCELLED", "SESSION_NOT_BOOKABLE",
                "BOOKING_ALREADY_CANCELLED", "BOOKING_ALREADY_CHECKED_IN",
                "CHECK_IN_WINDOW_NOT_OPEN", "WAITLIST_ALREADY_JOINED",
                "WAITLIST_ALREADY_PROCESSED", "MEMBERSHIP_ALREADY_ACTIVE",
                "CREDITS_INSUFFICIENT", "OVERLAPPING_BOOKING", "LATE_CANCEL_NO_REFUND",
                "INSTRUCTOR_SCHEDULE_CONFLICT", "ROOM_SCHEDULE_CONFLICT",
                "IDEMPOTENCY_KEY_CONFLICT", "JOB_ALREADY_RUNNING");

        // 422
        assertThat(names).contains(
                "VALIDATION_FAILED", "INVALID_DATE_RANGE", "INVALID_PAGINATION",
                "INVALID_SORT_FIELD", "UNKNOWN_FIELD");

        // 500
        assertThat(names).contains("INTERNAL_ERROR");
    }

    @Test
    void testAc1HttpStatusGroupsAreCorrect() {
        for (ErrorCode code : ErrorCode.values()) {
            int series = code.httpStatus().value() / 100;
            assertThat(series)
                    .as("ErrorCode.%s has unexpected HTTP status series %dxx", code.name(), series)
                    .isBetween(4, 5);
        }

        assertThat(ErrorCode.MALFORMED_REQUEST.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

        Set<ErrorCode> forbidden = Set.of(
                ErrorCode.NOT_PERMITTED, ErrorCode.MEMBER_SUSPENDED, ErrorCode.MEMBER_INACTIVE);
        for (ErrorCode code : forbidden) {
            assertThat(code.httpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        Set<ErrorCode> notFound = Set.of(
                ErrorCode.NOT_FOUND, ErrorCode.MEMBER_NOT_FOUND,
                ErrorCode.MEMBERSHIP_PLAN_NOT_FOUND, ErrorCode.MEMBERSHIP_NOT_FOUND,
                ErrorCode.CLASS_TYPE_NOT_FOUND, ErrorCode.INSTRUCTOR_NOT_FOUND,
                ErrorCode.ROOM_NOT_FOUND, ErrorCode.SESSION_NOT_FOUND,
                ErrorCode.BOOKING_NOT_FOUND, ErrorCode.WAITLIST_ENTRY_NOT_FOUND);
        for (ErrorCode code : notFound) {
            assertThat(code.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        Set<ErrorCode> conflict = Set.of(
                ErrorCode.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                ErrorCode.DUPLICATE_BOOKING, ErrorCode.SESSION_FULL,
                ErrorCode.SESSION_CANCELLED, ErrorCode.SESSION_NOT_BOOKABLE,
                ErrorCode.BOOKING_ALREADY_CANCELLED, ErrorCode.BOOKING_ALREADY_CHECKED_IN,
                ErrorCode.CHECK_IN_WINDOW_NOT_OPEN, ErrorCode.WAITLIST_ALREADY_JOINED,
                ErrorCode.WAITLIST_ALREADY_PROCESSED, ErrorCode.MEMBERSHIP_ALREADY_ACTIVE,
                ErrorCode.CREDITS_INSUFFICIENT, ErrorCode.OVERLAPPING_BOOKING,
                ErrorCode.LATE_CANCEL_NO_REFUND, ErrorCode.INSTRUCTOR_SCHEDULE_CONFLICT,
                ErrorCode.ROOM_SCHEDULE_CONFLICT, ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                ErrorCode.JOB_ALREADY_RUNNING);
        for (ErrorCode code : conflict) {
            assertThat(code.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
        }

        Set<ErrorCode> unprocessable = Set.of(
                ErrorCode.VALIDATION_FAILED, ErrorCode.INVALID_DATE_RANGE,
                ErrorCode.INVALID_PAGINATION, ErrorCode.INVALID_SORT_FIELD,
                ErrorCode.UNKNOWN_FIELD);
        for (ErrorCode code : unprocessable) {
            assertThat(code.httpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        assertThat(ErrorCode.INTERNAL_ERROR.httpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // =========================================================================
    // AC-2: Status is by construction — the field lives on the enum constant
    // =========================================================================

    @Test
    void testAc2HttpStatusIsIdenticalByConstruction() throws Exception {
        // Verify that ErrorCode has a private final httpStatus field (the invariant is structural)
        Field httpStatusField = ErrorCode.class.getDeclaredField("httpStatus");
        httpStatusField.setAccessible(true);

        for (ErrorCode code : ErrorCode.values()) {
            HttpStatus fieldValue = (HttpStatus) httpStatusField.get(code);
            assertThat(fieldValue)
                    .as("ErrorCode.%s: httpStatus() must return the value stored on the constant", code.name())
                    .isEqualTo(code.httpStatus());
        }
    }

    // =========================================================================
    // AC-3: Every code has a message template in error-messages.properties
    // =========================================================================

    @Test
    void testAc3AllCodesHaveMessageTemplate() {
        for (ErrorCode code : ErrorCode.values()) {
            String message = ErrorMessages.forCode(code);
            assertThat(message)
                    .as("ErrorCode.%s must have a non-empty message template", code.name())
                    .isNotBlank()
                    .isNotEqualTo(code.name());
        }
    }

    @Test
    void testAc3MessageTemplatesDoNotContainPlaceholderArtifacts() {
        for (ErrorCode code : ErrorCode.values()) {
            String message = ErrorMessages.forCode(code);
            assertThat(message).doesNotContain("TODO");
            assertThat(message).doesNotContain("FIXME");
            assertThat(message).doesNotContain("???");
        }
    }

    // =========================================================================
    // AC-5: ErrorEnvelope.code() is typed as ErrorCode — compile-time containment
    // =========================================================================

    @Test
    void testAc5EnvelopeCodeFieldIsTypedAsErrorCode() throws Exception {
        // Verify at runtime that the record component 'code' is of type ErrorCode, not String
        java.lang.reflect.RecordComponent[] components = ErrorEnvelope.class.getRecordComponents();
        java.lang.reflect.RecordComponent codeComponent = Arrays.stream(components)
                .filter(c -> c.getName().equals("code"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ErrorEnvelope has no 'code' record component"));

        assertThat(codeComponent.getType())
                .as("ErrorEnvelope.code must be typed as ErrorCode, not String")
                .isEqualTo(ErrorCode.class);
    }

    @Test
    void testAc5EnvelopeCanBeConstructedOnlyWithEnumCode() {
        // Building an envelope with a valid ErrorCode must not throw
        assertThatCode(() -> ErrorEnvelope.builder()
                .type("https://api.studio.example/errors/not-found")
                .title("Not Found")
                .status(404)
                .code(ErrorCode.NOT_FOUND)
                .detail("Resource not found")
                .instance("/api/v1/members/123")
                .timestamp(java.time.Instant.now())
                .traceId("abc")
                .build()
        ).doesNotThrowAnyException();
    }

    // =========================================================================
    // AC-6: toKebab() derivation for a representative sample including multi-word codes
    // =========================================================================

    @Test
    void testAc6TypeUriDerivationForRepresentativeSample() {
        // Single-word codes
        assertThat(ErrorCode.CONFLICT.toKebab()).isEqualTo("conflict");
        assertThat(ErrorCode.NOT_FOUND.toKebab()).isEqualTo("not-found");

        // Multi-word codes
        assertThat(ErrorCode.CONCURRENT_MODIFICATION.toKebab()).isEqualTo("concurrent-modification");
        assertThat(ErrorCode.VALIDATION_FAILED.toKebab()).isEqualTo("validation-failed");
        assertThat(ErrorCode.MALFORMED_REQUEST.toKebab()).isEqualTo("malformed-request");
        assertThat(ErrorCode.MEMBER_NOT_FOUND.toKebab()).isEqualTo("member-not-found");
        assertThat(ErrorCode.MEMBERSHIP_PLAN_NOT_FOUND.toKebab()).isEqualTo("membership-plan-not-found");
        assertThat(ErrorCode.CLASS_TYPE_NOT_FOUND.toKebab()).isEqualTo("class-type-not-found");
        assertThat(ErrorCode.BOOKING_ALREADY_CHECKED_IN.toKebab()).isEqualTo("booking-already-checked-in");
        assertThat(ErrorCode.CHECK_IN_WINDOW_NOT_OPEN.toKebab()).isEqualTo("check-in-window-not-open");
        assertThat(ErrorCode.INSTRUCTOR_SCHEDULE_CONFLICT.toKebab()).isEqualTo("instructor-schedule-conflict");
        assertThat(ErrorCode.IDEMPOTENCY_KEY_CONFLICT.toKebab()).isEqualTo("idempotency-key-conflict");
        assertThat(ErrorCode.LATE_CANCEL_NO_REFUND.toKebab()).isEqualTo("late-cancel-no-refund");
        assertThat(ErrorCode.INTERNAL_ERROR.toKebab()).isEqualTo("internal-error");
    }

    @Test
    void testAc6KebabDoesNotContainUnderscoresOrUppercase() {
        for (ErrorCode code : ErrorCode.values()) {
            String kebab = code.toKebab();
            assertThat(kebab)
                    .as("ErrorCode.%s toKebab() must not contain underscores", code.name())
                    .doesNotContain("_");
            assertThat(kebab)
                    .as("ErrorCode.%s toKebab() must be lowercase", code.name())
                    .isEqualTo(kebab.toLowerCase());
        }
    }
}
