package com.studio.booking.membership.infrastructure;

import com.studio.booking.membership.domain.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, UUID> {

    @Query("SELECT t FROM CreditTransaction t WHERE t.membershipId = :membershipId ORDER BY t.createdAt ASC")
    List<CreditTransaction> findByMembershipIdOrderByCreatedAtAsc(@Param("membershipId") UUID membershipId);
}
