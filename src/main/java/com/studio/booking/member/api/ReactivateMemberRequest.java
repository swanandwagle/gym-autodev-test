package com.studio.booking.member.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to reactivate a suspended member")
public record ReactivateMemberRequest() {
}
