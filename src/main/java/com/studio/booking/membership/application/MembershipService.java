package com.studio.booking.membership.application;

import com.studio.booking.member.infrastructure.MemberRepository;
import com.studio.booking.membership.api.AssignMembershipRequest;
import com.studio.booking.membership.domain.Membership;
import com.studio.booking.membership.domain.MembershipPlan;
import com.studio.booking.membership.infrastructure.MembershipPlanRepository;
import com.studio.booking.membership.infrastructure.MembershipRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.time.StudioTimeZone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final MembershipPlanRepository planRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;
    private final StudioTimeZone studioTimeZone;

    public MembershipService(
        MembershipRepository membershipRepository,
        MembershipPlanRepository planRepository,
        MemberRepository memberRepository,
        Clock clock,
        StudioTimeZone studioTimeZone
    ) {
        this.membershipRepository = membershipRepository;
        this.planRepository = planRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
        this.studioTimeZone = studioTimeZone;
    }

    @Transactional
    public Membership assignMembership(UUID memberId, AssignMembershipRequest request) {
        // Verify member exists
        memberRepository.findById(memberId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.MEMBER_NOT_FOUND,
                "No member exists for the given ID",
                null
            ));

        // Verify plan exists and is active
        UUID planId = request.planId();
        MembershipPlan plan = planRepository.findById(planId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.MEMBERSHIP_PLAN_NOT_FOUND,
                "No membership plan exists for the given ID",
                null
            ));

        if (!plan.isActive()) {
            throw new ApiException(
                ErrorCode.PLAN_INACTIVE,
                "The membership plan is inactive; this operation requires an active plan",
                null
            );
        }

        Instant now = Instant.now(clock);
        Instant requestedStartsAt = request.startsAt();

        // Lock membership state upfront to check queue limit
        var activeMembership = membershipRepository.findActiveByMemberIdWithWriteLock(memberId);
        var pendingMembership = membershipRepository.findPendingByMemberIdWithWriteLock(memberId);

        // Check queue limit (at most 1 ACTIVE + 1 PENDING)
        if (activeMembership.isPresent() && pendingMembership.isPresent()) {
            throw new ApiException(
                ErrorCode.MEMBERSHIP_ALREADY_QUEUED,
                "A member already has one ACTIVE and one PENDING membership; cannot queue a third",
                null
            );
        }

        // Determine actual startsAt
        Instant actualStartsAt;
        if (requestedStartsAt != null) {
            // Validate that startsAt is not too far in the past (max 5 minutes)
            Duration timeSincePast = Duration.between(requestedStartsAt, now);
            if (timeSincePast.toMinutes() > 5) {
                throw new ApiException(
                    ErrorCode.OUT_OF_RANGE,
                    "startsAt must not be more than 5 minutes in the past",
                    null
                );
            }
            actualStartsAt = requestedStartsAt;

            // If active membership exists, validate that startsAt is not before its expiry
            if (activeMembership.isPresent()) {
                Instant activeExpiry = activeMembership.get().getExpiresAt();
                if (actualStartsAt.isBefore(activeExpiry)) {
                    throw new ApiException(
                        ErrorCode.MEMBERSHIP_START_BEFORE_CURRENT_EXPIRY,
                        "The requested startsAt is before the current active membership's expiresAt",
                        null
                    );
                }
            }
        } else {
            // No startsAt provided; default based on existing memberships
            if (activeMembership.isPresent()) {
                // Queue after active membership expires
                actualStartsAt = activeMembership.get().getExpiresAt();
            } else {
                // Start now
                actualStartsAt = now;
            }
        }

        // Compute expiresAt: startsAt + durationDays in studio timezone
        Instant expiresAt = computeExpiry(actualStartsAt, plan.getDurationDays());

        // Determine membership status: ACTIVE if now or past, PENDING if future
        String status;
        if (actualStartsAt.isAfter(now)) {
            status = "PENDING";
        } else {
            status = "ACTIVE";
        }

        boolean unlimited = plan.getClassCredits() == null;
        Integer creditsInitial = unlimited ? null : plan.getClassCredits();
        Integer creditsRemaining = unlimited ? null : plan.getClassCredits();

        Membership membership = new Membership(
            memberId,
            planId,
            status,
            unlimited,
            creditsInitial,
            creditsRemaining,
            actualStartsAt,
            expiresAt
        );

        return membershipRepository.save(membership);
    }

    public Membership getMembership(UUID membershipId) {
        return membershipRepository.findById(membershipId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.MEMBERSHIP_NOT_FOUND,
                "No membership exists for the given ID",
                null
            ));
    }

    public List<Membership> getMemberHistory(UUID memberId, List<String> statusFilter) {
        // Verify member exists
        memberRepository.findById(memberId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.MEMBER_NOT_FOUND,
                "No member exists for the given ID",
                null
            ));

        if (statusFilter != null && !statusFilter.isEmpty()) {
            return membershipRepository.findByMemberIdAndStatusInOrderByStartsAtDesc(memberId, statusFilter);
        } else {
            return membershipRepository.findByMemberIdOrderByStartsAtDesc(memberId);
        }
    }

    @Transactional
    public Membership cancelMembership(UUID membershipId) {
        Membership membership = membershipRepository.findById(membershipId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.MEMBERSHIP_NOT_FOUND,
                "No membership exists for the given ID",
                null
            ));

        if (!"PENDING".equals(membership.getStatus())) {
            throw new ApiException(
                ErrorCode.MEMBERSHIP_NOT_CANCELLABLE,
                "Membership cancellation is only allowed for PENDING status; current status is " + membership.getStatus(),
                null
            );
        }

        membership.setStatus("CANCELLED");
        membership.setUpdatedAt(Instant.now(clock));

        return membershipRepository.save(membership);
    }

    private Instant computeExpiry(Instant startsAt, int durationDays) {
        ZoneId zoneId = studioTimeZone.zoneId();
        ZonedDateTime startZoned = startsAt.atZone(zoneId);
        LocalDate startDate = startZoned.toLocalDate();

        // Add durationDays to the start date
        LocalDate expiryDate = startDate.plusDays(durationDays);

        // Create expiry at same time of day as start time in the studio timezone
        ZonedDateTime expiryZoned = expiryDate.atTime(startZoned.toLocalTime()).atZone(zoneId);

        return expiryZoned.toInstant();
    }
}
