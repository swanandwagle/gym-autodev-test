package com.studio.booking.member.api;

import com.studio.booking.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Member response")
public record MemberResponse(
    @Schema(description = "Member unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    String id,

    @Schema(description = "Member email address (case-sensitive storage)", example = "john@example.com")
    String email,

    @Schema(description = "Member full name", example = "John Doe")
    String fullName,

    @Schema(description = "Member phone number")
    String phone,

    @Schema(description = "Member status", example = "ACTIVE")
    String status,

    @Schema(description = "Suspension reason (null if not suspended)")
    String suspensionReason,

    @Schema(description = "Suspension date/time in UTC (null if not suspended)", example = "2026-09-23T10:00:00Z")
    Instant suspendedAt,

    @Schema(description = "Member join date/time in UTC", example = "2026-09-23T10:00:00Z")
    Instant joinedAt,

    @Schema(description = "Member creation date/time in UTC (immutable)", example = "2026-09-23T10:00:00Z")
    Instant createdAt,

    @Schema(description = "Member last update date/time in UTC", example = "2026-09-23T10:00:00Z")
    Instant updatedAt,

    @Schema(description = "Optimistic lock version", example = "0")
    long version
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
            member.getId().toString(),
            member.getEmail(),
            member.getFullName(),
            member.getPhone(),
            member.getStatus(),
            member.getSuspensionReason(),
            member.getSuspendedAt(),
            member.getJoinedAt(),
            member.getCreatedAt(),
            member.getUpdatedAt(),
            member.getVersion()
        );
    }
}
