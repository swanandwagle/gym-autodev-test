package com.studio.booking.membership.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.studio.booking.membership.domain.MembershipPlan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Membership plan response")
public record MembershipPlanResponse(
    @Schema(description = "Plan ID", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id,

    @Schema(description = "Plan name", example = "Monthly 10-Pack")
    String name,

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Number of class credits (null for unlimited plans)", example = "10")
    Integer classCredits,

    @Schema(description = "True if this is an unlimited-credits plan (classCredits is null)", example = "false")
    boolean unlimited,

    @Schema(description = "Plan validity duration in days", example = "30")
    int durationDays,

    @Schema(description = "Plan price")
    MoneyDto price,

    @Schema(description = "Plan tier (BASIC, PREMIUM, VIP)", example = "BASIC")
    String tier,

    @Schema(description = "Whether the plan is active (available for purchase)", example = "true")
    boolean active,

    @Schema(description = "Creation timestamp (UTC)", example = "2026-09-23T10:15:30.123Z")
    Instant createdAt,

    @Schema(description = "Last update timestamp (UTC)", example = "2026-09-23T10:15:30.123Z")
    Instant updatedAt,

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "Optimistic lock version", example = "0")
    long version
) {

    public static MembershipPlanResponse from(MembershipPlan plan) {
        return new MembershipPlanResponse(
            plan.getId(),
            plan.getName(),
            plan.getClassCredits(),
            plan.getClassCredits() == null,
            plan.getDurationDays(),
            new MoneyDto(plan.getPrice(), plan.getCurrency()),
            plan.getTier(),
            plan.isActive(),
            plan.getCreatedAt(),
            plan.getUpdatedAt(),
            plan.getVersion()
        );
    }
}
