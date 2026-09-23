package com.studio.booking.member.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Register a new member request")
public record RegisterMemberRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Schema(description = "Member email address", example = "john@example.com")
    String email,

    @NotBlank(message = "Full name is required")
    @Schema(description = "Member full name (will be trimmed)", example = "John Doe")
    String fullName,

    @Schema(description = "Member phone number", example = "+1234567890")
    String phone
) {
}
