package com.studio.booking.member.api;

import com.studio.booking.member.application.MemberService;
import com.studio.booking.member.domain.Member;
import com.studio.booking.shared.error.ErrorEnvelope;
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

@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "Members", description = "Member registration and management")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
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
}
