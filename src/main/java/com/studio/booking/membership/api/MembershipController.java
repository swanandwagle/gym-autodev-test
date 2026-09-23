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
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "Memberships", description = "Membership assignment and management")
public class MembershipController {

    private final MembershipService membershipService;
    private final Clock clock;

    public MembershipController(MembershipService membershipService, Clock clock) {
        this.membershipService = membershipService;
        this.clock = clock;
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

    @GetMapping("/{memberId}/memberships/{membershipId}")
    @Operation(
        summary = "Retrieve a membership by ID",
        description = "Retrieves a single membership by its ID. Returns effective status (e.g., ACTIVE→EXPIRED when expiry has passed, PENDING→ACTIVE when start time has passed). The stored row is not modified."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Membership retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MembershipResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Membership not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Invalid membership ID format",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        )
    })
    public ResponseEntity<MembershipResponse> getMembership(
        @PathVariable @ValidUuid UUID memberId,
        @PathVariable @ValidUuid UUID membershipId
    ) {
        Membership membership = membershipService.getMembership(membershipId);
        MembershipResponse response = MembershipResponse.fromWithClock(membership, clock);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{memberId}/memberships")
    @Operation(
        summary = "List member's membership history",
        description = "Retrieves all memberships for a member, ordered by startsAt descending. " +
                      "Returns effective status for each membership. " +
                      "Optional status filter can be provided as comma-separated values (e.g., ?status=EXPIRED,CANCELLED). " +
                      "Sort parameter is not supported and will return 422 if provided."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Member's membership history retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MembershipListResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Member not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Validation error (e.g., sort parameter provided or invalid UUID format)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        )
    })
    public ResponseEntity<MembershipListResponse> getMemberHistory(
        @PathVariable @ValidUuid UUID memberId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String sort
    ) {
        if (sort != null) {
            throw new ApiException(
                ErrorCode.INVALID_SORT_FIELD,
                "The sort parameter is not supported for this endpoint",
                null
            );
        }

        List<String> statusFilter = null;
        if (status != null && !status.isEmpty()) {
            statusFilter = List.of(status.split(","));
        }

        List<Membership> memberships = membershipService.getMemberHistory(memberId, statusFilter);
        List<MembershipResponse> responses = memberships.stream()
            .map(m -> MembershipResponse.fromWithClock(m, clock))
            .toList();

        return ResponseEntity.ok(new MembershipListResponse(responses));
    }

    @DeleteMapping("/{memberId}/memberships/{membershipId}/cancel")
    @Operation(
        summary = "Cancel a PENDING membership",
        description = "Cancels a membership that is in PENDING status. " +
                      "Only PENDING memberships can be cancelled; ACTIVE, EXPIRED, or CANCELLED memberships will be rejected with 409. " +
                      "Cancellation is not reversible and does not restore credits. " +
                      "After cancellation, the member can be assigned a new membership without triggering MEMBERSHIP_ALREADY_QUEUED conflict."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Membership successfully cancelled",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MembershipResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Membership not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Membership is not in PENDING status and cannot be cancelled",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Invalid membership ID format",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        )
    })
    public ResponseEntity<MembershipResponse> cancelMembership(
        @PathVariable @ValidUuid UUID memberId,
        @PathVariable @ValidUuid UUID membershipId
    ) {
        Membership membership = membershipService.cancelMembership(membershipId);
        MembershipResponse response = MembershipResponse.fromWithClock(membership, clock);
        return ResponseEntity.ok(response);
    }
}
