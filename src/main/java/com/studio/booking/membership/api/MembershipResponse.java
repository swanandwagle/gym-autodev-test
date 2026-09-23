package com.studio.booking.membership.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.studio.booking.membership.domain.Membership;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Membership response")
public record MembershipResponse(
    @Schema(description = "Membership unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id,

    @Schema(description = "Member unique identifier", example = "550e8400-e29b-41d4-a716-446655440001")
    UUID memberId,

    @Schema(description = "Membership plan unique identifier (snapshotted at assignment time)", example = "550e8400-e29b-41d4-a716-446655440002")
    UUID planId,

    @Schema(description = "Membership status (ACTIVE, PENDING, EXPIRED, CANCELLED)", example = "ACTIVE")
    String status,

    @Schema(description = "True if this is an unlimited-credits membership (creditsRemaining is null)", example = "false")
    boolean unlimited,

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Initial credits at assignment (null for unlimited memberships)", example = "10")
    Integer creditsInitial,

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Remaining credits (null for unlimited memberships)", example = "10")
    Integer creditsRemaining,

    @Schema(description = "Membership start time (UTC)", example = "2026-09-23T10:00:00Z")
    Instant startsAt,

    @Schema(description = "Membership expiry time (UTC)", example = "2026-10-23T10:00:00Z")
    Instant expiresAt,

    @Schema(description = "Creation timestamp (UTC)", example = "2026-09-23T10:15:30.123Z")
    Instant createdAt,

    @Schema(description = "Last update timestamp (UTC)", example = "2026-09-23T10:15:30.123Z")
    Instant updatedAt,

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "Optimistic lock version", example = "0")
    long version
) {

    public static MembershipResponse from(Membership membership) {
        return fromWithClock(membership, Clock.systemUTC());
    }

    public static MembershipResponse fromWithClock(Membership membership, Clock clock) {
        String effectiveStatus = computeEffectiveStatus(membership, clock);
        return new MembershipResponse(
            membership.getId(),
            membership.getMemberId(),
            membership.getPlanId(),
            effectiveStatus,
            membership.isUnlimited(),
            membership.getCreditsInitial(),
            membership.getCreditsRemaining(),
            membership.getStartsAt(),
            membership.getExpiresAt(),
            membership.getCreatedAt(),
            membership.getUpdatedAt(),
            membership.getVersion()
        );
    }

    private static String computeEffectiveStatus(Membership membership, Clock clock) {
        String storedStatus = membership.getStatus();
        Instant now = Instant.now(clock);

        if ("ACTIVE".equals(storedStatus)) {
            if (now.isAfter(membership.getExpiresAt()) || now.equals(membership.getExpiresAt())) {
                return "EXPIRED";
            }
            return "ACTIVE";
        } else if ("PENDING".equals(storedStatus)) {
            if (now.isAfter(membership.getStartsAt()) || now.equals(membership.getStartsAt())) {
                return "ACTIVE";
            }
            return "PENDING";
        }

        return storedStatus;
    }
}
