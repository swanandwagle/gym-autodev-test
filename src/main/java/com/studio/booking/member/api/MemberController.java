package com.studio.booking.member.api;

import com.studio.booking.member.application.MemberService;
import com.studio.booking.member.domain.Member;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.error.ErrorEnvelope;
import com.studio.booking.shared.error.FieldError;
import com.studio.booking.shared.validation.ValidUuid;
import com.studio.booking.shared.web.AllowedSortFields;
import com.studio.booking.shared.web.PageParamsValidator;
import com.studio.booking.shared.web.PageResponse;
import com.studio.booking.shared.web.SortValidator;
import com.studio.booking.shared.web.SortablePageParams;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "Members", description = "Member registration and management")
public class MemberController {

    private final MemberService memberService;
    private final PageParamsValidator pageParamsValidator;
    private final SortValidator sortValidator;

    public MemberController(
        MemberService memberService,
        PageParamsValidator pageParamsValidator,
        SortValidator sortValidator
    ) {
        this.memberService = memberService;
        this.pageParamsValidator = pageParamsValidator;
        this.sortValidator = sortValidator;
    }

    @GetMapping
    @Operation(
        summary = "List and search members",
        description = "Lists members with optional full-text search on name or email, status filtering, and pagination. " +
                      "The 'q' parameter matches substring in both directions (case-insensitive); omit or leave blank to skip filtering. " +
                      "Default sort is by joinedAt descending; valid sort fields are: joinedAt, email, fullName."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Paginated member list returned",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = PageResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Validation error (invalid pagination, sort field, or q length)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        )
    })
    public ResponseEntity<PageResponse<MemberResponse>> listMembers(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String status,
        @AllowedSortFields({"joinedAt", "email", "fullName"})
        SortablePageParams params
    ) {
        pageParamsValidator.validate(params);
        sortValidator.validate(params, new String[]{"joinedAt", "email", "fullName"});

        if (q != null && q.length() > 100) {
            throw new ApiException(
                ErrorCode.TOO_LONG,
                "Search query exceeds maximum length of 100 characters",
                List.of(
                    FieldError.of(
                        "q",
                        "TOO_LONG",
                        "Query must not exceed 100 characters"
                    )
                )
            );
        }

        String normalizedQuery = (q == null || q.isBlank()) ? "" : q;
        String normalizedStatus = (status == null || status.isBlank()) ? null : status;

        Sort sort;
        String sortField = params.sortField();
        String sortDirection = params.sortDirection();
        if (sortField != null) {
            sort = Sort.by(new Sort.Order(
                "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC,
                sortField
            ));
        } else {
            sort = Sort.by(new Sort.Order(Sort.Direction.DESC, "joinedAt"));
        }

        var pageable = PageRequest.of(params.getPage(), params.getSize(), sort);
        Page<Member> memberPage = memberService.searchMembers(normalizedQuery, normalizedStatus, pageable);

        List<MemberResponse> content = memberPage.getContent()
            .stream()
            .map(MemberResponse::from)
            .toList();

        return ResponseEntity.ok(PageResponse.of(content, params.getPage(), params.getSize(), memberPage.getTotalElements()));
    }

    @PostMapping
    @Operation(
        summary = "Register a new member",
        description = "Creates a new member with the provided details. Email addresses are unique case-insensitively."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Member successfully created",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MemberResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Email already exists (case-insensitive)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Validation error or unknown field",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Malformed JSON request",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        )
    })
    public ResponseEntity<MemberResponse> registerMember(
        @Valid @RequestBody RegisterMemberRequest request
    ) {
        Member member = memberService.registerMember(request);
        MemberResponse response = MemberResponse.from(member);
        URI location = URI.create("/api/v1/members/" + member.getId());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Retrieve a member profile",
        description = "Retrieves the full profile of an existing member by ID."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Member found and returned",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MemberResponse.class)
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
            description = "Invalid UUID format",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        )
    })
    public ResponseEntity<MemberResponse> getMember(@PathVariable @ValidUuid UUID id) {
        Member member = memberService.getById(id);
        return ResponseEntity.ok(MemberResponse.from(member));
    }

    @PatchMapping("/{id}")
    @Operation(
        summary = "Update member profile",
        description = "Partially updates a member's profile with optimistic locking. " +
                      "Only the provided fields are updated; omitted fields are left unchanged. " +
                      "Status is not updatable and will be rejected as an unknown field. " +
                      "Version is mandatory and must match the current version to succeed."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Member successfully updated",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MemberResponse.class)
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
            responseCode = "409",
            description = "Concurrent modification (stale version) or email conflict",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Validation error (missing version, invalid format, or unknown field)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        )
    })
    public ResponseEntity<MemberResponse> updateMember(
        @PathVariable @ValidUuid UUID id,
        @Valid @RequestBody UpdateMemberRequest request
    ) {
        if (!request.hasAnyUpdate()) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "At least one field to update is required",
                List.of(
                    FieldError.of(
                        "body",
                        "REQUIRED",
                        "At least one field (email, fullName, or phone) is required"
                    )
                )
            );
        }
        Member member = memberService.updateMember(id, request);
        return ResponseEntity.ok(MemberResponse.from(member));
    }

    @PostMapping("/{id}/suspend")
    @Operation(
        summary = "Suspend a member",
        description = "Suspends an active member, preventing further transactions. " +
                      "Suspension reason is optional and limited to 255 characters. " +
                      "Can only suspend ACTIVE members; suspending an already-suspended member returns 409."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Member successfully suspended",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MemberResponse.class)
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
            responseCode = "409",
            description = "Member already suspended",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Validation error (reason too long)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        )
    })
    public ResponseEntity<MemberResponse> suspendMember(
        @PathVariable @ValidUuid UUID id,
        @RequestBody(required = false) SuspendMemberRequest request,
        HttpServletRequest httpRequest
    ) {
        String reason = null;
        if (request != null && request.reason() != null) {
            reason = request.reason();
            if (reason.length() > 255) {
                throw new ApiException(
                    ErrorCode.TOO_LONG,
                    "Suspension reason exceeds maximum length of 255 characters",
                    List.of(
                        FieldError.of(
                            "reason",
                            "TOO_LONG",
                            "Reason must not exceed 255 characters"
                        )
                    )
                );
            }
        }
        Member member = memberService.suspendMember(id, reason);
        return ResponseEntity.ok(MemberResponse.from(member));
    }

    @PostMapping("/{id}/reactivate")
    @Operation(
        summary = "Reactivate a suspended member",
        description = "Reactivates a suspended member, allowing transactions to resume. " +
                      "Can only reactivate SUSPENDED members; reactivating an active member returns 409."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Member successfully reactivated",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MemberResponse.class)
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
            responseCode = "409",
            description = "Member is not suspended",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        )
    })
    public ResponseEntity<MemberResponse> reactivateMember(
        @PathVariable @ValidUuid UUID id,
        @RequestBody(required = false) ReactivateMemberRequest request
    ) {
        Member member = memberService.reactivateMember(id);
        return ResponseEntity.ok(MemberResponse.from(member));
    }
}
