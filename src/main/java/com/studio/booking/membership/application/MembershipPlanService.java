package com.studio.booking.membership.application;

import com.studio.booking.membership.api.CreateMembershipPlanRequest;
import com.studio.booking.membership.api.UpdateMembershipPlanRequest;
import com.studio.booking.membership.domain.MembershipPlan;
import com.studio.booking.membership.infrastructure.MembershipPlanRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MembershipPlanService {

    private final MembershipPlanRepository planRepository;

    public MembershipPlanService(MembershipPlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Transactional
    public MembershipPlan createPlan(CreateMembershipPlanRequest request) {
        // Check for duplicate name (case-insensitive)
        if (planRepository.findByNameCaseInsensitive(request.name()).isPresent()) {
            throw new ApiException(
                ErrorCode.PLAN_NAME_ALREADY_EXISTS,
                "A membership plan with this name already exists",
                null
            );
        }

        // Validate durationDays
        int durationDays = request.durationDays();
        if (durationDays <= 0 || durationDays > 3660) {
            throw new ApiException(
                ErrorCode.OUT_OF_RANGE,
                "Duration in days must be between 1 and 3660",
                null
            );
        }

        // Validate classCredits if provided (null is OK for unlimited)
        if (request.classCredits() != null && request.classCredits() <= 0) {
            throw new ApiException(
                ErrorCode.OUT_OF_RANGE,
                "Class credits must be greater than 0",
                null
            );
        }

        MembershipPlan plan = new MembershipPlan(
            request.name(),
            request.classCredits(),
            durationDays,
            request.price().amount(),
            request.price().currency(),
            request.tier()
        );

        return planRepository.save(plan);
    }

    @Transactional(readOnly = true)
    public MembershipPlan getById(UUID id) {
        return planRepository.findById(id)
            .orElseThrow(() -> new ApiException(
                ErrorCode.MEMBERSHIP_PLAN_NOT_FOUND,
                "No membership plan exists for the given ID",
                null
            ));
    }

    @Transactional(readOnly = true)
    public Page<MembershipPlan> listPlans(boolean includeInactive, Boolean unlimited, String tier, Pageable pageable) {
        if (unlimited != null && unlimited) {
            // Filter for unlimited plans (classCredits IS NULL)
            if (tier != null) {
                return includeInactive
                    ? planRepository.findAllUnlimitedByTierIncludingInactive(tier, pageable)
                    : planRepository.findAllUnlimitedByTierActive(tier, pageable);
            } else {
                return includeInactive
                    ? planRepository.findAllUnlimitedIncludingInactive(pageable)
                    : planRepository.findAllUnlimitedActive(pageable);
            }
        } else if (tier != null) {
            // Filter by tier only
            return includeInactive
                ? planRepository.findAllByTierIncludingInactive(tier, pageable)
                : planRepository.findAllByTierActive(tier, pageable);
        } else {
            // No filters, just active/all
            return includeInactive
                ? planRepository.findAllIncludingInactive(pageable)
                : planRepository.findAllActive(pageable);
        }
    }

    @Transactional
    public MembershipPlan updatePlan(UUID id, UpdateMembershipPlanRequest request) {
        MembershipPlan plan = getById(id);

        // Update name if provided
        if (request.hasName()) {
            String newName = request.name().trim();
            // Check for duplicate name only if it's different from current name (case-insensitive)
            if (!newName.equalsIgnoreCase(plan.getName())) {
                if (planRepository.findByNameCaseInsensitive(newName).isPresent()) {
                    throw new ApiException(
                        ErrorCode.PLAN_NAME_ALREADY_EXISTS,
                        "A membership plan with this name already exists",
                        null
                    );
                }
            }
            plan.updateName(newName);
        }

        // Update classCredits (tri-state: omit = unchanged, explicit value = set, null = unlimited)
        if (request.hasClassCredits()) {
            plan.updateClassCredits(request.classCredits());
        }

        return planRepository.save(plan);
    }

    @Transactional
    public MembershipPlan deactivatePlan(UUID id) {
        MembershipPlan plan = getById(id);

        if (!plan.isActive()) {
            throw new ApiException(
                ErrorCode.PLAN_ALREADY_INACTIVE,
                "This membership plan is already inactive",
                null
            );
        }

        plan.deactivate();
        return planRepository.save(plan);
    }
}
