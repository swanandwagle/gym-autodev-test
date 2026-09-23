package com.studio.booking.membership.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "List of memberships")
public record MembershipListResponse(
    @Schema(description = "Array of membership records")
    List<MembershipResponse> content
) {}
