package com.studio.booking.member.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to suspend a member")
public record SuspendMemberRequest(
    @Schema(description = "Reason for suspension (optional, max 255 chars)")
    String reason
) {
}
