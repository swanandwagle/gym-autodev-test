package com.studio.booking.member.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

import java.util.Optional;

@Schema(description = "Update member profile request")
public record UpdateMemberRequest(
    @Email(message = "Email must be a valid email address")
    @Schema(description = "Member email address (optional)", example = "john@example.com")
    String email,

    @Schema(description = "Member full name (optional)", example = "John Doe")
    String fullName,

    @Schema(description = "Member phone number (optional)", example = "+1234567890")
    String phone,

    @NotNull(message = "Version is required")
    @Schema(description = "Optimistic lock version (mandatory)", example = "0")
    Long version
) {
    public Optional<String> getEmail() {
        return Optional.ofNullable(email);
    }

    public Optional<String> getFullName() {
        return Optional.ofNullable(fullName);
    }

    public Optional<String> getPhone() {
        return Optional.ofNullable(phone);
    }

    public boolean hasAnyUpdate() {
        return email != null || fullName != null || phone != null;
    }
}
