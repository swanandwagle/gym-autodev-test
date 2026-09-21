package com.studio.booking.membership.infrastructure;

import com.studio.booking.membership.domain.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, UUID> {
}
