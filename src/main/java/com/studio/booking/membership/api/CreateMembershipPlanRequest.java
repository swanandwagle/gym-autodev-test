package com.studio.booking.membership.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

@Schema(description = "Create a new membership plan")
public record CreateMembershipPlanRequest(
    @NotBlank(message = "Plan name is required")
    @Schema(description = "Plan name (unique case-insensitively)", example = "Monthly 10-Pack")
    String name,

    @Min(value = 1, message = "Class credits must be greater than 0")
    @Schema(description = "Number of class credits (omit or null for unlimited plan)", example = "10")
    Integer classCredits,

    @NotNull(message = "Duration in days is required")
    @Schema(description = "Plan validity duration in days (1-3660)", example = "30")
    Integer durationDays,

    @NotNull(message = "Price is required")
    @ValidPrice
    @Schema(description = "Plan price with amount and currency")
    MoneyDto price,

    @Schema(description = "Plan tier (BASIC, PREMIUM, VIP); omit for BASIC", example = "BASIC")
    String tier
) {
}
