# Error Code Catalogue

Generated from `ErrorCode` enum. Do not edit manually — regenerate with `ErrorCataloguePrinter.generate()`.

## HTTP 400

| Code | Kebab slug | Default message |
|------|------------|----------------|
| `MALFORMED_REQUEST` | `malformed-request` | Request body could not be read or is malformed JSON. |

## HTTP 403

| Code | Kebab slug | Default message |
|------|------------|----------------|
| `NOT_PERMITTED` | `not-permitted` | The requested action is not permitted. |
| `MEMBER_SUSPENDED` | `member-suspended` | The member account is suspended and cannot perform this action. |
| `MEMBER_INACTIVE` | `member-inactive` | The member account is inactive and cannot perform this action. |

## HTTP 404

| Code | Kebab slug | Default message |
|------|------------|----------------|
| `NOT_FOUND` | `not-found` | The requested resource was not found. |
| `MEMBER_NOT_FOUND` | `member-not-found` | No member found with the given identifier. |
| `MEMBERSHIP_PLAN_NOT_FOUND` | `membership-plan-not-found` | No membership plan found with the given identifier. |
| `MEMBERSHIP_NOT_FOUND` | `membership-not-found` | No membership found with the given identifier. |
| `CLASS_TYPE_NOT_FOUND` | `class-type-not-found` | No class type found with the given identifier. |
| `INSTRUCTOR_NOT_FOUND` | `instructor-not-found` | No instructor found with the given identifier. |
| `ROOM_NOT_FOUND` | `room-not-found` | No room found with the given identifier. |
| `SESSION_NOT_FOUND` | `session-not-found` | No class session found with the given identifier. |
| `BOOKING_NOT_FOUND` | `booking-not-found` | No booking found with the given identifier. |
| `WAITLIST_ENTRY_NOT_FOUND` | `waitlist-entry-not-found` | No waitlist entry found for the given identifier. |

## HTTP 409

| Code | Kebab slug | Default message |
|------|------------|----------------|
| `CONFLICT` | `conflict` | The request conflicts with the current state. |
| `CONCURRENT_MODIFICATION` | `concurrent-modification` | The resource was modified concurrently. Please re-fetch and retry. |
| `DUPLICATE_BOOKING` | `duplicate-booking` | A non-cancelled booking already exists for this member and session. |
| `SESSION_FULL` | `session-full` | The session has no remaining capacity. |
| `SESSION_CANCELLED` | `session-cancelled` | The session has been cancelled and cannot be booked. |
| `SESSION_NOT_BOOKABLE` | `session-not-bookable` | The session is not open for booking. |
| `BOOKING_ALREADY_CANCELLED` | `booking-already-cancelled` | The booking has already been cancelled. |
| `BOOKING_ALREADY_CHECKED_IN` | `booking-already-checked-in` | The booking has already been checked in. |
| `CHECK_IN_WINDOW_NOT_OPEN` | `check-in-window-not-open` | The check-in window for this session is not currently open. |
| `WAITLIST_ALREADY_JOINED` | `waitlist-already-joined` | The member is already on the waitlist for this session. |
| `WAITLIST_ALREADY_PROCESSED` | `waitlist-already-processed` | The waitlist entry has already been processed or expired. |
| `MEMBERSHIP_ALREADY_ACTIVE` | `membership-already-active` | The member already has an active membership. |
| `CREDITS_INSUFFICIENT` | `credits-insufficient` | The membership does not have enough credits for this operation. |
| `OVERLAPPING_BOOKING` | `overlapping-booking` | This session overlaps with an existing confirmed booking for the member. |
| `LATE_CANCEL_NO_REFUND` | `late-cancel-no-refund` | Cancellation is past the refund window; no credit refund will be issued. |
| `INSTRUCTOR_SCHEDULE_CONFLICT` | `instructor-schedule-conflict` | The instructor already has a session scheduled in this time slot. |
| `ROOM_SCHEDULE_CONFLICT` | `room-schedule-conflict` | The room is already occupied in the requested time slot. |
| `IDEMPOTENCY_KEY_CONFLICT` | `idempotency-key-conflict` | The idempotency key was previously used with different request parameters. |
| `JOB_ALREADY_RUNNING` | `job-already-running` | The scheduled job is already running. |

## HTTP 422

| Code | Kebab slug | Default message |
|------|------------|----------------|
| `VALIDATION_FAILED` | `validation-failed` | Request validation failed. See errors. |
| `INVALID_DATE_RANGE` | `invalid-date-range` | The supplied date or time range is logically invalid. |
| `INVALID_PAGINATION` | `invalid-pagination` | Pagination parameters are outside the allowed bounds. |
| `INVALID_SORT_FIELD` | `invalid-sort-field` | The requested sort field is not supported by this endpoint. |
| `UNKNOWN_FIELD` | `unknown-field` | The request body contains an unrecognised field. |

## HTTP 500

| Code | Kebab slug | Default message |
|------|------------|----------------|
| `INTERNAL_ERROR` | `internal-error` | An unexpected error occurred. |

