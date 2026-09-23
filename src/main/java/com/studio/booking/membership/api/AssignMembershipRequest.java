package com.studio.booking.membership.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Assign a membership plan to a member")
public record AssignMembershipRequest(
    @NotNull(message = "Plan ID is required")
    @Schema(description = "Membership plan unique identifier", example = "550e8400-e29b-41d4-a716-446655440001")
    UUID planId,

    @Schema(description = "Membership start time (ISO-8601 UTC). If omitted: starts now (if no active membership) or at active membership's expiry (if one exists). Must not be more than 5 minutes in the past.", example = "2026-09-23T10:00:00Z")
    Instant startsAt
) {
}
