package com.studio.booking.membership.application;

import com.studio.booking.membership.domain.Membership;

import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for credit and membership operations consumed by booking and waitlist modules.
 * This port provides:
 * - Membership lookup with pessimistic locking for booking operations
 * - Credit availability checks
 * - Guarded credit deduction and refund with ledger writes
 *
 * All credit operations write to the append-only credit_transaction ledger
 * and maintain the membership.credits_remaining balance atomically.
 */
public interface CreditPort {

    /**
     * Load a usable membership for booking operations.
     * Returns the ACTIVE membership for the given member if:
     * - Status is ACTIVE
     * - startsAt <= now < expiresAt (upper bound is exclusive)
     *
     * Acquires a pessimistic write lock to prevent concurrent modifications.
     *
     * @param memberId the member ID
     * @return the membership if usable, empty otherwise
     */
    Optional<Membership> loadUsableForBooking(UUID memberId);

    /**
     * Check if the membership has available credit.
     * Throws MEMBERSHIP_NO_CREDITS if the membership has zero credits and is not unlimited.
     * Returns successfully for unlimited memberships regardless of balance.
     *
     * @param membershipId the membership ID
     * @throws com.studio.booking.shared.error.ApiException with MEMBERSHIP_NO_CREDITS if no credits available
     */
    void requireCredit(UUID membershipId);

    /**
     * Check if the membership has available credit without throwing.
     * Returns false if the membership has zero credits and is not unlimited.
     * Returns true for unlimited memberships regardless of balance.
     *
     * @param membershipId the membership ID
     * @return true if credit is available, false otherwise
     */
    boolean hasCredit(UUID membershipId);

    /**
     * Deduct one credit from the membership with the given reason.
     * For credit-based memberships:
     * - Reduces credits_remaining by 1
     * - Writes a credit_transaction row with delta=-1, reason, and balanceAfter
     * - Uses guarded update (check affected row count) to detect race conditions
     * - Throws MEMBERSHIP_NO_CREDITS if deduction would violate credits >= 0
     *
     * For unlimited memberships:
     * - No changes to the membership
     * - No ledger row written
     *
     * @param membershipId the membership ID
     * @param reason the reason code (BOOKING, SESSION_CANCELLED_REFUND, etc.)
     * @throws com.studio.booking.shared.error.ApiException with MEMBERSHIP_NO_CREDITS if no credits available
     */
    void deduct(UUID membershipId, String reason);

    /**
     * Refund one credit to the membership with the given reason.
     * For credit-based memberships:
     * - Increases credits_remaining by 1
     * - Writes a credit_transaction row with delta=+1, reason, and balanceAfter
     *
     * For unlimited memberships:
     * - No changes to the membership
     * - No ledger row written
     *
     * @param membershipId the membership ID
     * @param reason the reason code (CANCEL_REFUND, SESSION_CANCELLED_REFUND, STAFF_ADJUSTMENT, etc.)
     */
    void refund(UUID membershipId, String reason);
}
