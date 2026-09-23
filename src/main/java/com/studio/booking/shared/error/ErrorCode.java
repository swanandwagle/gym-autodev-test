package com.studio.booking.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Canonical error-code catalogue for the Fitness Class Booking System.
 *
 * Each constant carries its fixed HTTP status so the status is identical by construction
 * regardless of which endpoint emits the code. The type URI path segment is derived via
 * {@link #toKebab()}.
 *
 * Status semantics:
 *   400 – malformed / unreadable body
 *   403 – domain-level "not permitted" (member state forbids the action)
 *   404 – path-referenced resource does not exist
 *   409 – well-formed request but current state forbids it (conflict / concurrency)
 *   422 – input validation failure; always accompanied by errors[]
 *   500 – unhandled; no internal detail exposed
 */
public enum ErrorCode {

    // -------------------------------------------------------------------------
    // 400 — Malformed / unreadable input
    // -------------------------------------------------------------------------

    /** Unreadable body or malformed JSON. */
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),

    // -------------------------------------------------------------------------
    // 403 — Domain-level "not permitted"
    // -------------------------------------------------------------------------

    /** Generic domain-level "not allowed"; use a more specific code when available. */
    NOT_PERMITTED(HttpStatus.FORBIDDEN),

    /** The member account is suspended; action requires an active account. */
    MEMBER_SUSPENDED(HttpStatus.FORBIDDEN),

    /** The member account is inactive; action requires an active account. */
    MEMBER_INACTIVE(HttpStatus.FORBIDDEN),

    // -------------------------------------------------------------------------
    // 404 — Resource not found (path-referenced)
    // -------------------------------------------------------------------------

    /** Generic not-found; use a more specific code when available. */
    NOT_FOUND(HttpStatus.NOT_FOUND),

    /** No member exists for the given ID. */
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** No membership plan exists for the given ID. */
    MEMBERSHIP_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** No membership exists for the given ID. */
    MEMBERSHIP_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** No class type exists for the given ID. */
    CLASS_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** No instructor exists for the given ID. */
    INSTRUCTOR_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** No room exists for the given ID. */
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** No class session exists for the given ID. */
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** No booking exists for the given ID. */
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** No waitlist entry exists for the given ID or (member, session) pair. */
    WAITLIST_ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND),

    // -------------------------------------------------------------------------
    // 409 — Conflict / current-state forbids the well-formed request
    // -------------------------------------------------------------------------

    /** Generic business-rule or state conflict; use a more specific code when available. */
    CONFLICT(HttpStatus.CONFLICT),

    /** Optimistic-lock failure; client must re-fetch and retry. */
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),

    /** The member already has a non-cancelled booking for this session. */
    DUPLICATE_BOOKING(HttpStatus.CONFLICT),

    /** The session has no remaining capacity. */
    SESSION_FULL(HttpStatus.CONFLICT),

    /** The session has been cancelled and cannot be booked. */
    SESSION_CANCELLED(HttpStatus.CONFLICT),

    /** The session has already started and is no longer bookable. */
    SESSION_NOT_BOOKABLE(HttpStatus.CONFLICT),

    /** The booking is already in a cancelled state. */
    BOOKING_ALREADY_CANCELLED(HttpStatus.CONFLICT),

    /** The booking has already been checked in. */
    BOOKING_ALREADY_CHECKED_IN(HttpStatus.CONFLICT),

    /** The check-in window for this session is not open. */
    CHECK_IN_WINDOW_NOT_OPEN(HttpStatus.CONFLICT),

    /** The member is already on the waitlist for this session. */
    WAITLIST_ALREADY_JOINED(HttpStatus.CONFLICT),

    /** The waitlist entry has already expired or been processed. */
    WAITLIST_ALREADY_PROCESSED(HttpStatus.CONFLICT),

    /** The member already has an active membership. */
    MEMBERSHIP_ALREADY_ACTIVE(HttpStatus.CONFLICT),

    /** The membership does not have enough credits for this operation. */
    CREDITS_INSUFFICIENT(HttpStatus.CONFLICT),

    /** The session overlaps with another confirmed booking for this member. */
    OVERLAPPING_BOOKING(HttpStatus.CONFLICT),

    /** Cancellation is past the refund window; no refund will be issued. */
    LATE_CANCEL_NO_REFUND(HttpStatus.CONFLICT),

    /** The instructor already has a session in the requested time slot. */
    INSTRUCTOR_SCHEDULE_CONFLICT(HttpStatus.CONFLICT),

    /** The room is already occupied in the requested time slot. */
    ROOM_SCHEDULE_CONFLICT(HttpStatus.CONFLICT),

    /** The class type with this name already exists (case-insensitive). */
    CLASS_TYPE_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT),

    /** The class type is inactive; this operation requires an active class type. */
    CLASS_TYPE_INACTIVE(HttpStatus.CONFLICT),

    /** The instructor email already exists (case-insensitive). */
    INSTRUCTOR_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),

    /** The instructor is inactive; this operation requires an active instructor. */
    INSTRUCTOR_INACTIVE(HttpStatus.CONFLICT),

    /** The instructor has one or more future scheduled sessions and cannot be deactivated. */
    INSTRUCTOR_HAS_FUTURE_SESSIONS(HttpStatus.CONFLICT),

    /** The room name already exists (case-insensitive). */
    ROOM_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT),

    /** The room is inactive; this operation requires an active room. */
    ROOM_INACTIVE(HttpStatus.CONFLICT),

    /** The room has one or more future scheduled sessions and cannot be deactivated. */
    ROOM_HAS_FUTURE_SESSIONS(HttpStatus.CONFLICT),

    /**
     * An idempotency key was reused with different request parameters.
     * (Same key + same member = 200 replay; different params = 409.)
     */
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT),

    /** The scheduled job is already running; concurrent execution is not allowed. */
    JOB_ALREADY_RUNNING(HttpStatus.CONFLICT),

    /** A member with this email address already exists (case-insensitive). */
    MEMBER_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),

    /** The member is already suspended; cannot suspend again. */
    MEMBER_ALREADY_SUSPENDED(HttpStatus.CONFLICT),

    /** The member is not suspended; cannot reactivate an active member. */
    MEMBER_NOT_SUSPENDED(HttpStatus.CONFLICT),

    // -------------------------------------------------------------------------
    // 422 — Input validation failure (request wrong regardless of server state)
    // -------------------------------------------------------------------------

    /** Bean-validation failure; always includes errors[]. */
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),

    /** A numeric value is outside the allowed range. */
    OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_ENTITY),

    /** A date or time range is logically invalid (e.g. end before start). */
    INVALID_DATE_RANGE(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Pagination parameters are outside the allowed bounds. */
    INVALID_PAGINATION(HttpStatus.UNPROCESSABLE_ENTITY),

    /** The requested sort field is not supported by this endpoint. */
    INVALID_SORT_FIELD(HttpStatus.UNPROCESSABLE_ENTITY),

    /** The request body contains an unrecognised field. */
    UNKNOWN_FIELD(HttpStatus.UNPROCESSABLE_ENTITY),

    // -------------------------------------------------------------------------
    // 500 — Unhandled / internal
    // -------------------------------------------------------------------------

    /** Unhandled exception; no internal detail exposed in the response. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    // -------------------------------------------------------------------------

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    /** The HTTP status that must be used whenever this code appears in a response. */
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    /** Converts the enum name to the kebab-case path segment used in the type URI. */
    public String toKebab() {
        return name().toLowerCase().replace('_', '-');
    }
}
