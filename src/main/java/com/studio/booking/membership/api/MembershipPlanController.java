package com.studio.booking.membership.api;

import com.studio.booking.membership.application.MembershipPlanService;
import com.studio.booking.membership.domain.MembershipPlan;
import com.studio.booking.shared.error.ErrorEnvelope;
import com.studio.booking.shared.validation.ValidUuid;
import com.studio.booking.shared.web.AllowedSortFields;
import com.studio.booking.shared.web.PageParamsValidator;
import com.studio.booking.shared.web.PageResponse;
import com.studio.booking.shared.web.SortValidator;
import com.studio.booking.shared.web.SortablePageParams;
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
@RequestMapping("/api/v1/membership-plans")
@Tag(name = "Membership Plans", description = "Membership plan catalogue and management")
public class MembershipPlanController {

    private final MembershipPlanService planService;
    private final PageParamsValidator pageParamsValidator;
    private final SortValidator sortValidator;

    public MembershipPlanController(
        MembershipPlanService planService,
        PageParamsValidator pageParamsValidator,
        SortValidator sortValidator
    ) {
        this.planService = planService;
        this.pageParamsValidator = pageParamsValidator;
        this.sortValidator = sortValidator;
    }

    @PostMapping
    @Operation(
        summary = "Create a new membership plan",
        description = "Creates a new membership plan. Name must be unique (case-insensitive). " +
                      "classCredits is optional: omit or pass null for unlimited plans. " +
                      "durationDays must be between 1 and 3660. " +
                      "price.amount is required and must have at most 2 decimal places. " +
                      "price.currency must be uppercase 3-letter ISO 4217 code. " +
                      "tier defaults to BASIC if omitted."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Membership plan successfully created",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MembershipPlanResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Plan name already exists (case-insensitive)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Validation error (invalid price format, out-of-range values, or unknown field)",
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
    public ResponseEntity<MembershipPlanResponse> createPlan(
        @Valid @RequestBody CreateMembershipPlanRequest request
    ) {
        MembershipPlan plan = planService.createPlan(request);
        MembershipPlanResponse response = MembershipPlanResponse.from(plan);
        URI location = URI.create("/api/v1/membership-plans/" + plan.getId());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Retrieve a membership plan",
        description = "Retrieves the full details of an existing membership plan by ID."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Membership plan found and returned",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = MembershipPlanResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Membership plan not found",
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
    public ResponseEntity<MembershipPlanResponse> getPlan(
        @PathVariable @ValidUuid UUID id
    ) {
        MembershipPlan plan = planService.getById(id);
        return ResponseEntity.ok(MembershipPlanResponse.from(plan));
    }

    @GetMapping
    @Operation(
        summary = "List membership plans",
        description = "Lists membership plans with optional filtering and pagination. " +
                      "By default, only active plans are returned; pass includeInactive=true to include inactive. " +
                      "unlimited=true filters to unlimited-credits plans only (classCredits is null). " +
                      "tier=BASIC|PREMIUM|VIP filters by plan tier. " +
                      "Note: when sorting by price, results are ordered by amount and then by currency (lexicographically). " +
                      "Valid sort fields: name, durationDays, price (amount), tier, createdAt, updatedAt. " +
                      "Default sort is by createdAt descending."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Paginated membership plan list returned",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = PageResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Validation error (invalid pagination, sort field, or enum value)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        )
    })
    public ResponseEntity<PageResponse<MembershipPlanResponse>> listPlans(
        @RequestParam(required = false, defaultValue = "false") boolean includeInactive,
        @RequestParam(required = false) Boolean unlimited,
        @RequestParam(required = false) String tier,
        @AllowedSortFields({"name", "durationDays", "price", "tier", "createdAt", "updatedAt"})
        SortablePageParams params
    ) {
        pageParamsValidator.validate(params);
        sortValidator.validate(params, new String[]{"name", "durationDays", "price", "tier", "createdAt", "updatedAt"});

        Sort sort;
        String sortField = params.sortField();
        String sortDirection = params.sortDirection();
        if (sortField != null) {
            String jpaField = sortField;
            if ("price".equals(sortField)) {
                jpaField = "price";
            }
            sort = Sort.by(new Sort.Order(
                "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC,
                jpaField
            ));
        } else {
            sort = Sort.by(new Sort.Order(Sort.Direction.DESC, "createdAt"));
        }

        var pageable = PageRequest.of(params.getPage(), params.getSize(), sort);
        Page<MembershipPlan> planPage = planService.listPlans(includeInactive, unlimited, tier, pageable);

        List<MembershipPlanResponse> content = planPage.getContent()
            .stream()
            .map(MembershipPlanResponse::from)
            .toList();

        return ResponseEntity.ok(PageResponse.of(content, params.getPage(), params.getSize(), planPage.getTotalElements()));
    }
}
