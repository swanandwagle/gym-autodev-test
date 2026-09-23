package com.studio.booking.membership.infrastructure;

import com.studio.booking.membership.domain.Membership;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Membership m WHERE m.memberId = :memberId AND m.status = 'ACTIVE'")
    Optional<Membership> findActiveByMemberIdWithWriteLock(@Param("memberId") UUID memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Membership m WHERE m.memberId = :memberId AND m.status = 'PENDING'")
    Optional<Membership> findPendingByMemberIdWithWriteLock(@Param("memberId") UUID memberId);

    @Query("SELECT COUNT(m) > 0 FROM Membership m WHERE m.memberId = :memberId AND m.status IN ('ACTIVE', 'PENDING')")
    boolean hasMembershipQueued(@Param("memberId") UUID memberId);

    @Query("SELECT m FROM Membership m WHERE m.memberId = :memberId ORDER BY m.startsAt DESC")
    List<Membership> findByMemberIdOrderByStartsAtDesc(@Param("memberId") UUID memberId);

    @Query("SELECT m FROM Membership m WHERE m.memberId = :memberId AND m.status IN (:statuses) ORDER BY m.startsAt DESC")
    List<Membership> findByMemberIdAndStatusInOrderByStartsAtDesc(@Param("memberId") UUID memberId, @Param("statuses") List<String> statuses);
}
