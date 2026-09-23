package com.studio.booking.membership.infrastructure;

import com.studio.booking.membership.domain.MembershipPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, UUID> {

    @Query("SELECT p FROM MembershipPlan p WHERE LOWER(p.name) = LOWER(:name)")
    Optional<MembershipPlan> findByNameCaseInsensitive(@Param("name") String name);

    @Query("SELECT p FROM MembershipPlan p WHERE p.active = true")
    Page<MembershipPlan> findAllActive(Pageable pageable);

    @Query("SELECT p FROM MembershipPlan p")
    Page<MembershipPlan> findAllIncludingInactive(Pageable pageable);

    @Query("SELECT p FROM MembershipPlan p WHERE p.classCredits IS NULL AND p.active = true")
    Page<MembershipPlan> findAllUnlimitedActive(Pageable pageable);

    @Query("SELECT p FROM MembershipPlan p WHERE p.classCredits IS NULL")
    Page<MembershipPlan> findAllUnlimitedIncludingInactive(Pageable pageable);

    @Query("SELECT p FROM MembershipPlan p WHERE p.tier = :tier AND p.active = true")
    Page<MembershipPlan> findAllByTierActive(@Param("tier") String tier, Pageable pageable);

    @Query("SELECT p FROM MembershipPlan p WHERE p.tier = :tier")
    Page<MembershipPlan> findAllByTierIncludingInactive(@Param("tier") String tier, Pageable pageable);

    @Query("SELECT p FROM MembershipPlan p WHERE p.classCredits IS NULL AND p.tier = :tier AND p.active = true")
    Page<MembershipPlan> findAllUnlimitedByTierActive(@Param("tier") String tier, Pageable pageable);

    @Query("SELECT p FROM MembershipPlan p WHERE p.classCredits IS NULL AND p.tier = :tier")
    Page<MembershipPlan> findAllUnlimitedByTierIncludingInactive(@Param("tier") String tier, Pageable pageable);
}
