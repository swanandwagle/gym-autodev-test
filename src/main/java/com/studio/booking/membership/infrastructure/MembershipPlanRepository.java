package com.studio.booking.membership.infrastructure;

import com.studio.booking.membership.domain.MembershipPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, UUID> {
}
