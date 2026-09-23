package com.studio.booking.catalog.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateInstructorRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        @NotBlank(message = "name is required")
        String name,

        String bio,

        @Size(max = 20, message = "specialties must contain at most 20 items")
        List<@Size(max = 40, message = "each specialty must be at most 40 characters") String> specialties
) {}
