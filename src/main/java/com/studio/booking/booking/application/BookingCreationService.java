package com.studio.booking.booking.application;

import com.studio.booking.booking.domain.Booking;
import com.studio.booking.booking.infrastructure.BookingRepository;
import com.studio.booking.catalog.domain.ClassSession;
import com.studio.booking.catalog.infrastructure.ClassSessionRepository;
import com.studio.booking.member.application.MemberStatusGate;
import com.studio.booking.member.domain.Member;
import com.studio.booking.membership.application.CreditPort;
import com.studio.booking.membership.domain.Membership;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared eligibility rules for booking creation, used by both the booking endpoint and
 * the waitlist promotion routine.
 *
 * This service encapsulates the eligibility checks so that both code paths (endpoint and
 * promotion) apply the same rules and produce consistent verdicts.
 *
 * CRITICAL: The overlap check ({@link #checkOverlap(UUID, ClassSession)}) must be called
 * while the member row is held under pessimistic lock. This is not enforced by this class
 * but is documented here as a hard constraint. See {@link #checkOverlap(UUID, ClassSession)}
 * for details.
 *
 * Each eligibility check method returns an {@link EligibilityResult} that contains:
 * - The verdict (eligible or skip)
 * - If skipped: the SkipReason enum and corresponding ApiException code
 *
 * A booking is eligible if and only if all five checks pass:
 * 1. Member must be ACTIVE (gate.requireActive)
 * 2. Membership must be ACTIVE and have credits (creditPort.requireCredit)
 * 3. Member must not already be on the waitlist for this session (optional, not checked here)
 * 4. Member must not have an existing non-cancelled booking on this session (duplicate check)
 * 5. Member must not have an overlapping BOOKED booking (overlap check) – MUST be called under lock
 *
 * This class does not perform the actual booking creation; it only validates eligibility.
 */
@Service
public class BookingCreationService {

    private final BookingRepository bookingRepository;
    private final ClassSessionRepository classSessionRepository;
    private final CreditPort creditPort;
    private final MemberStatusGate memberStatusGate;

    public BookingCreationService(
        BookingRepository bookingRepository,
        ClassSessionRepository classSessionRepository,
        CreditPort creditPort,
        MemberStatusGate memberStatusGate
    ) {
        this.bookingRepository = bookingRepository;
        this.classSessionRepository = classSessionRepository;
        this.creditPort = creditPort;
        this.memberStatusGate = memberStatusGate;
    }

    /**
     * Checks if a member is eligible to book a session. This method is used by both the
     * booking endpoint and the waitlist promotion routine to ensure consistent verdicts.
     *
     * The caller must ensure the member row is held under pessimistic lock before calling
     * this method, so that the eligibility verdict cannot become stale due to concurrent
     * member or membership state changes.
     *
     * @param memberId the member ID
     * @param sessionId the session ID
     * @return an EligibilityResult containing the verdict and, if skipped, the reason
     */
    public EligibilityResult checkEligibility(UUID memberId, UUID sessionId) {
        // Check 1: Member must be ACTIVE
        Member member = memberStatusGate.loadForTransaction(memberId);
        try {
            memberStatusGate.requireActive(member);
        } catch (ApiException e) {
            return EligibilityResult.skipped(SkipReason.MEMBER_INACTIVE, ErrorCode.MEMBER_INACTIVE);
        }

        // Check 2: Membership must be ACTIVE and have credits
        Optional<Membership> membershipOpt = creditPort.loadUsableForBooking(memberId);
        if (membershipOpt.isEmpty()) {
            return EligibilityResult.skipped(SkipReason.MEMBERSHIP_INACTIVE, ErrorCode.MEMBER_INACTIVE);
        }
        Membership membership = membershipOpt.get();

        try {
            creditPort.requireCredit(membership.getId());
        } catch (ApiException e) {
            return EligibilityResult.skipped(SkipReason.INSUFFICIENT_CREDITS, ErrorCode.CREDITS_INSUFFICIENT);
        }

        // Check 3: Load session (will be locked by caller if needed)
        ClassSession session = classSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.SESSION_NOT_FOUND,
                "No class session found with the given identifier",
                null
            ));

        // Check 4: Member must not have an existing non-cancelled booking on this session
        boolean existingNonCancelledBooking = bookingRepository.findBookedBySessionId(sessionId).stream()
            .anyMatch(b -> b.getMemberId().equals(memberId) && !"CANCELLED".equals(b.getStatus()));
        if (existingNonCancelledBooking) {
            return EligibilityResult.skipped(SkipReason.DUPLICATE_BOOKING, ErrorCode.DUPLICATE_BOOKING);
        }

        // Check 5: Member must not have an overlapping BOOKED booking
        // CRITICAL: This query must be called while the member row is held under pessimistic lock.
        // The overlap check must run at the same logical instant as the lock acquisition, so that
        // concurrent bookings cannot slide in between the lock and the query. If this is called
        // without the lock, the overlap verdict can become stale before the actual booking is created.
        EligibilityResult overlapResult = checkOverlap(memberId, session);
        if (!overlapResult.isEligible()) {
            return overlapResult;
        }

        return EligibilityResult.eligible();
    }

    /**
     * Checks if the member has any overlapping BOOKED bookings with the given session.
     *
     * CRITICAL CONSTRAINT: This method MUST be called while the member row is held under
     * pessimistic lock (SELECT ... FOR UPDATE). This ensures that:
     * 1. The query results are consistent with the logical instant of the lock acquisition
     * 2. No concurrent booking can be inserted between this check and the actual booking creation
     * 3. The overlap check and booking creation are atomic from the member's perspective
     *
     * If this is called without the lock, the overlap check can become stale:
     * - Thread A checks overlap (finds none) and acquires lock on member
     * - Thread B creates a booking that overlaps with Thread A's attempted session
     * - Thread A's subsequent booking creation violates the overlap rule
     *
     * The overlap interval is half-open: [start, end). This means:
     * - Sessions 10:00-11:00 and 11:00-12:00 do NOT overlap (back-to-back is allowed)
     * - Sessions 10:00-11:00 and 10:01-11:01 DO overlap (any intersection in [start, end))
     *
     * @param memberId the member ID
     * @param newSession the session to check for overlaps
     * @return an EligibilityResult with BOOKING_OVERLAPS_EXISTING if overlap found, else eligible()
     */
    private EligibilityResult checkOverlap(UUID memberId, ClassSession newSession) {
        List<Booking> memberBookings = bookingRepository.findByMemberId(memberId);

        for (Booking existingBooking : memberBookings) {
            // Only consider BOOKED bookings; CANCELLED, ATTENDED, NO_SHOW do not block
            if (!"BOOKED".equals(existingBooking.getStatus())) {
                continue;
            }

            // Load the existing booking's session to get its time range
            ClassSession existingSession = classSessionRepository.findById(existingBooking.getSessionId())
                .orElse(null);
            if (existingSession == null) {
                continue;
            }

            // Check for overlap using half-open interval [start, end)
            // Overlap exists if: new.start < existing.end AND existing.start < new.end
            if (newSession.getStartsAt().isBefore(existingSession.getEndsAt()) &&
                existingSession.getStartsAt().isBefore(newSession.getEndsAt())) {
                // Overlap detected; return error with both booking and session IDs
                return EligibilityResult.overlappingBooking(
                    existingBooking.getId(),
                    existingBooking.getSessionId()
                );
            }
        }

        return EligibilityResult.eligible();
    }

    /**
     * Encapsulates the result of an eligibility check.
     *
     * An eligible result means the member can book the session.
     * A skipped result means the member cannot book and provides the reason.
     * The overlap result is a special case of skipped that includes the conflicting booking and session IDs.
     */
    public static class EligibilityResult {
        private final boolean eligible;
        private final SkipReason skipReason;
        private final ErrorCode errorCode;
        private final UUID conflictingBookingId;
        private final UUID conflictingSessionId;

        private EligibilityResult(boolean eligible, SkipReason skipReason, ErrorCode errorCode,
                                  UUID conflictingBookingId, UUID conflictingSessionId) {
            this.eligible = eligible;
            this.skipReason = skipReason;
            this.errorCode = errorCode;
            this.conflictingBookingId = conflictingBookingId;
            this.conflictingSessionId = conflictingSessionId;
        }

        public boolean isEligible() {
            return eligible;
        }

        public SkipReason getSkipReason() {
            return skipReason;
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }

        public UUID getConflictingBookingId() {
            return conflictingBookingId;
        }

        public UUID getConflictingSessionId() {
            return conflictingSessionId;
        }

        public static EligibilityResult eligible() {
            return new EligibilityResult(true, null, null, null, null);
        }

        public static EligibilityResult skipped(SkipReason reason, ErrorCode code) {
            return new EligibilityResult(false, reason, code, null, null);
        }

        public static EligibilityResult overlappingBooking(UUID bookingId, UUID sessionId) {
            return new EligibilityResult(false, SkipReason.BOOKING_OVERLAPS_EXISTING,
                ErrorCode.BOOKING_OVERLAPS_EXISTING, bookingId, sessionId);
        }
    }

    /**
     * Enumeration of reasons why a member's booking eligibility check failed.
     *
     * Each reason maps to a specific ApiException code and waitlist_entry.skip_reason value.
     * The mapping must be kept consistent across both the booking endpoint and the promotion routine.
     */
    public enum SkipReason {
        /** Member account is not ACTIVE. */
        MEMBER_INACTIVE,
        /** Membership is not ACTIVE or expired. */
        MEMBERSHIP_INACTIVE,
        /** Membership has insufficient credits. */
        INSUFFICIENT_CREDITS,
        /** Member has an overlapping BOOKED booking. */
        BOOKING_OVERLAPS_EXISTING
    }
}
