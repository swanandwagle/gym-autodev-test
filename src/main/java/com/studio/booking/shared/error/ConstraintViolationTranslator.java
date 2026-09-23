package com.studio.booking.shared.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

/**
 * Translates a {@link DataIntegrityViolationException} into a specific {@link ErrorCode}
 * by extracting the PostgreSQL constraint name from the exception chain and looking it up
 * in the mapping table below.
 *
 * Constraint → ErrorCode mapping
 * (keep this table in sync with docs/constraint-mapping.md):
 *
 *   uq_booking_member_session        → DUPLICATE_BOOKING          (409)
 *   uq_membership_member_active      → MEMBERSHIP_ALREADY_ACTIVE  (409)
 *   uq_membership_member_pending     → MEMBERSHIP_ALREADY_ACTIVE  (409)
 *   uq_waitlist_member_session       → WAITLIST_ALREADY_JOINED     (409)
 *   excl_session_instructor_overlap  → INSTRUCTOR_SCHEDULE_CONFLICT(409)
 *   excl_session_room_overlap        → ROOM_SCHEDULE_CONFLICT      (409)
 *   uq_no_show_booking               → (swallowed — see isNoShowDuplicate)
 *   <unmapped>                       → CONCURRENT_MODIFICATION     (409)
 */
@Component
public class ConstraintViolationTranslator {

    private static final Logger log = LoggerFactory.getLogger(ConstraintViolationTranslator.class);

    /** Constraint name used by the no-show sweep; duplicates are expected and swallowed. */
    public static final String NO_SHOW_BOOKING_CONSTRAINT = "uq_no_show_booking";

    private static final Map<String, ErrorCode> MAPPING = Map.of(
            "uq_booking_member_session",       ErrorCode.DUPLICATE_BOOKING,
            "uq_membership_member_active",     ErrorCode.MEMBERSHIP_ALREADY_ACTIVE,
            "uq_membership_member_pending",    ErrorCode.MEMBERSHIP_ALREADY_ACTIVE,
            "uq_waitlist_member_session",      ErrorCode.WAITLIST_ALREADY_JOINED,
            "excl_session_instructor_overlap", ErrorCode.INSTRUCTOR_SCHEDULE_CONFLICT,
            "excl_session_room_overlap",       ErrorCode.ROOM_SCHEDULE_CONFLICT
    );

    /**
     * Returns true when the exception is a duplicate on {@code uq_no_show_booking}.
     * The no-show sweep calls this to decide whether to swallow the exception quietly.
     */
    public boolean isNoShowDuplicate(DataIntegrityViolationException ex) {
        return extractConstraintName(ex)
                .map(NO_SHOW_BOOKING_CONSTRAINT::equals)
                .orElse(false);
    }

    /**
     * Translates the exception to a mapped {@link ErrorCode}, or returns
     * {@link ErrorCode#CONCURRENT_MODIFICATION} for unmapped constraints.
     * The constraint name is logged at ERROR when unmapped.
     */
    public ErrorCode translate(DataIntegrityViolationException ex) {
        Optional<String> constraintName = extractConstraintName(ex);

        if (constraintName.isEmpty()) {
            log.error("DataIntegrityViolationException with no extractable constraint name", ex);
            return ErrorCode.CONCURRENT_MODIFICATION;
        }

        String name = constraintName.get();
        ErrorCode code = MAPPING.get(name);
        if (code != null) {
            return code;
        }

        log.error("Unmapped database constraint violation: constraint='{}' — returning CONCURRENT_MODIFICATION", name, ex);
        return ErrorCode.CONCURRENT_MODIFICATION;
    }

    /**
     * Walks the full exception chain looking for a {@link SQLException} whose
     * {@code serverErrorMessage} (PostgreSQL-specific) carries a constraint name.
     * Falls back to parsing the SQL state message string when the PSQL-specific
     * class is unavailable (e.g. non-PostgreSQL test environments).
     */
    public Optional<String> extractConstraintName(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            // Primary: PostgreSQL JDBC driver exposes ServerErrorMessage with the constraint name
            if (t instanceof SQLException sqle) {
                String name = extractFromSqlException(sqle);
                if (name != null) {
                    return Optional.of(name);
                }
            }
        }
        return Optional.empty();
    }

    private String extractFromSqlException(SQLException sqle) {
        // PSQLException extends SQLException and has getServerErrorMessage().getConstraint().
        // We access it reflectively to avoid a compile-time dependency on the PostgreSQL
        // driver's internal classes in this shared module.
        try {
            java.lang.reflect.Method getMsg = sqle.getClass().getMethod("getServerErrorMessage");
            Object serverMsg = getMsg.invoke(sqle);
            if (serverMsg != null) {
                java.lang.reflect.Method getConstraint = serverMsg.getClass().getMethod("getConstraint");
                Object constraint = getConstraint.invoke(serverMsg);
                if (constraint instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Not a PSQLException — fall through to string parsing
        }
        return null;
    }
}
