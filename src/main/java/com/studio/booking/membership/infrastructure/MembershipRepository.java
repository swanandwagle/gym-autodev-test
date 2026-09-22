package com.studio.booking.membership.infrastructure;

import com.studio.booking.membership.domain.Membership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
}
