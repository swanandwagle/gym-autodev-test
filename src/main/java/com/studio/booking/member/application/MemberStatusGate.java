package com.studio.booking.member.application;

import com.studio.booking.member.domain.Member;
import com.studio.booking.shared.error.ApiException;

import java.util.UUID;

/**
 * Port for checking member status and acquiring status-check locks.
 *
 * Clients in booking and waitlist modules use this port to enforce the suspension gate
 * without reaching directly into the member module's infrastructure.
 *
 * Lock ordering rule: When locking multiple members in a single transaction, always lock
 * in ascending order by member ID to prevent deadlocks. Lock operations acquire a
 * pessimistic write lock at the database level and block until committed.
 */
public interface MemberStatusGate {

    /**
     * Load a member and acquire a pessimistic write lock on the row.
     *
     * The lock is held until transaction commit; a second transaction attempting
     * to load the same member will block until the first releases it.
     *
     * This method is transactional; the lock is released when the calling transaction
     * commits or rolls back.
     *
     * @param memberId the member ID
     * @return the member, with write lock acquired
     * @throws ApiException with NOT_FOUND if member does not exist
     */
    Member loadForTransaction(UUID memberId);

    /**
     * Check that the member is active, and throw if suspended.
     *
     * Does NOT acquire a lock; caller must have already locked the member via
     * loadForTransaction if transactional consistency is required.
     *
     * @param member the member to check
     * @throws ApiException with MEMBER_SUSPENDED (403) if status is SUSPENDED
     */
    void requireActive(Member member);

    /**
     * Return whether the member is active.
     *
     * Never throws; safe to call on any member regardless of status.
     * Provided for callers who need to branch on status rather than assert it.
     *
     * @param member the member to check
     * @return true if status is ACTIVE, false otherwise (including SUSPENDED)
     */
    boolean isActive(Member member);
}
