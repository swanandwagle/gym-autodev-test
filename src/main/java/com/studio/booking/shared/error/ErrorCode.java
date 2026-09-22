package com.studio.booking.shared.error;

/**
 * Canonical error codes used in the RFC 9457 error envelope.
 *
 * Status-semantics table:
 *
 * | HTTP | Code                   | Origin / meaning                                              |
 * |------|------------------------|---------------------------------------------------------------|
 * |  400 | MALFORMED_REQUEST      | Unreadable body / malformed JSON                              |
 * |  403 | NOT_PERMITTED          | Domain-level "not allowed" (e.g. suspended member)            |
 * |  404 | NOT_FOUND              | Path-referenced resource does not exist                       |
 * |  409 | CONCURRENT_MODIFICATION| Optimistic-lock failure / stale-update detected               |
 * |  409 | CONFLICT               | Business-rule / state conflict / uniqueness violation          |
 * |  422 | VALIDATION_FAILED      | Bean-validation failure; always includes errors[]             |
 * |  500 | INTERNAL_ERROR         | Unhandled exception; no internal detail exposed               |
 */
public enum ErrorCode {

    MALFORMED_REQUEST,
    NOT_PERMITTED,
    NOT_FOUND,
    CONCURRENT_MODIFICATION,
    CONFLICT,
    VALIDATION_FAILED,
    INTERNAL_ERROR;

    /** Converts enum name to the kebab-case path segment used in the type URI. */
    public String toKebab() {
        return name().toLowerCase().replace('_', '-');
    }
}
