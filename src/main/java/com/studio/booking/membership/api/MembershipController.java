package com.studio.booking.membership.api;

import com.studio.booking.membership.application.MembershipService;
import com.studio.booking.membership.domain.Membership;
import com.studio.booking.shared.error.ErrorEnvelope;
import com.studio.booking.shared.validation.ValidUuid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "Memberships", description = "Membership assignment and management")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @PostMapping("/{memberId}/memberships")
    @Operation(
        summary = "Assign a membership plan to a member",
        description = "Assigns a membership plan to a member, computing the expiry based on plan duration. " +
                      "startsAt defaults to now (if no active membership) or to the active membership's expiresAt. " +
                      "A member can have at most one ACTIVE and one PENDING membership. " +
                      "startsAt must not be more than 5 minutes in the past. " +
                      "Credits are snapshotted from the plan at assignment time; plan changes do not affect existing memberships."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Membership successfully assigned",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MembershipResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Member or plan not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Conflict: plan inactive, member already has queued memberships, or startsAt before current expiry",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Validation error: invalid UUID format or startsAt too far in the past",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        )
    })
    public ResponseEntity<MembershipResponse> assignMembership(
        @PathVariable @ValidUuid UUID memberId,
        @Valid @RequestBody AssignMembershipRequest request
    ) {
        Membership membership = membershipService.assignMembership(memberId, request);
        MembershipResponse response = MembershipResponse.from(membership);
        URI location = URI.create("/api/v1/members/" + memberId + "/memberships/" + membership.getId());
        return ResponseEntity.status(HttpStatus.CREATED).location(location).body(response);
    }
}
