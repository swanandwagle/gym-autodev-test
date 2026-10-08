package com.studio.booking.catalog.api;

import com.studio.booking.catalog.api.request.BrowseSessionsRequest;
import com.studio.booking.catalog.api.request.CancelSessionRequest;
import com.studio.booking.catalog.api.request.CreateClassSessionRequest;
import com.studio.booking.catalog.api.request.CreateRecurringSessionsRequest;
import com.studio.booking.catalog.api.request.PatchClassSessionRequest;
import com.studio.booking.catalog.api.response.CancelSessionResponse;
import com.studio.booking.catalog.api.response.ClassSessionScheduleResponse;
import com.studio.booking.catalog.api.response.CreateRecurringSessionsResponse;
import com.studio.booking.catalog.api.response.PatchClassSessionResponse;
import com.studio.booking.catalog.application.BrowseSessionsService;
import com.studio.booking.catalog.application.ClassSessionService;
import com.studio.booking.catalog.application.RecurringSessionGenerator;
import com.studio.booking.shared.validation.ValidUuid;
import com.studio.booking.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "Schedule and manage fitness class sessions")
public class ClassSessionController {

    private final ClassSessionService service;
    private final RecurringSessionGenerator recurringGenerator;
    private final BrowseSessionsService browseService;

    public ClassSessionController(ClassSessionService service, RecurringSessionGenerator recurringGenerator, BrowseSessionsService browseService) {
        this.service = service;
        this.recurringGenerator = recurringGenerator;
        this.browseService = browseService;
    }

    @GetMapping("/browse")
    @Operation(
        summary = "Browse upcoming fitness class sessions",
        description = """
            Query and filter upcoming class sessions with pagination.

            Default behavior (no parameters): returns SCHEDULED sessions for the next 7 days.

            Time range semantics:
            - If neither from nor to is provided, defaults to [now, now + 7 days)
            - from and to must both be provided or both be absent
            - All boundaries use half-open semantics: [from, to), where from is inclusive and to is exclusive
            - Date shorthand (date=YYYY-MM-DD) returns sessions for that entire studio-local day and cannot be combined with from/to
            - Query span cannot exceed 92 days

            Filter behavior:
            - Unknown filter IDs (classTypeId, instructorId, roomId) return 200 with empty page, not 404
            - Filters are combined with AND logic (all must match)
            - availableOnly=true excludes full sessions (bookedCount == capacity)
            - status defaults to SCHEDULED if not provided

            Sort:
            - Only startsAt is supported; defaults to startsAt,asc
            - sort syntax: field,asc or field,desc

            Pagination:
            - page: zero-based page number (default 0)
            - size: results per page, between 1 and 100 (default 20)

            All timestamps in response are in UTC regardless of the offset used in from/to.
            """
    )
    @ApiResponse(responseCode = "200", description = "Sessions retrieved successfully")
    @ApiResponse(responseCode = "422", description = "Validation failed (e.g. invalid range, out of range span, invalid sort)")
    public ResponseEntity<PageResponse<ClassSessionScheduleResponse>> browse(BrowseSessionsRequest request) {
        PageResponse<ClassSessionScheduleResponse> response = browseService.browse(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(
        summary = "Schedule a new class session",
        description = """
            Schedule a new class session with an instructor and room.

            Time intervals use half-open semantics [start, end):
            - 10:00-11:00 and 11:00-12:00 do NOT conflict (adjacent sessions are allowed)
            - 10:00-11:00 and 10:59-11:59 DO conflict (overlapping sessions are rejected)

            If durationMinutes or capacity are omitted, defaults from the class type are used.
            """
    )
    @ApiResponse(responseCode = "201", description = "Session created successfully")
    @ApiResponse(responseCode = "422", description = "Input validation failed (e.g. past time, non-zero seconds)")
    @ApiResponse(responseCode = "409", description = "Conflict (e.g. instructor/room conflict, capacity exceeds room, inactive resource)")
    public ResponseEntity<ClassSessionScheduleResponse> create(@Valid @RequestBody CreateClassSessionRequest request) {
        ClassSessionScheduleResponse response = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/sessions/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a session by ID")
    @ApiResponse(responseCode = "200", description = "Session retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @ApiResponse(responseCode = "422", description = "Malformed UUID")
    public ResponseEntity<ClassSessionScheduleResponse> getById(@PathVariable @ValidUuid String id) {
        ClassSessionScheduleResponse response = service.getById(UUID.fromString(id));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/recurring")
    @Operation(
        summary = "Create recurring weekly sessions",
        description = """
            Generate multiple sessions for a recurring weekly pattern (e.g. Mon/Wed/Fri).
            Occurrences are filtered to exclude the past. DST transitions are handled per local timezone.
            Conflicts are detected and either failed (onConflict=FAIL) or skipped (onConflict=SKIP).
            """
    )
    @ApiResponse(responseCode = "201", description = "Sessions created successfully")
    @ApiResponse(responseCode = "409", description = "Conflict detected with onConflict=FAIL")
    @ApiResponse(responseCode = "422", description = "Input validation failed")
    public ResponseEntity<CreateRecurringSessionsResponse> createRecurring(@Valid @RequestBody CreateRecurringSessionsRequest request) {
        CreateRecurringSessionsResponse response = recurringGenerator.generate(request, null);
        return ResponseEntity
                .created(URI.create("/api/v1/sessions/by-recurrence/" + response.recurrenceId()))
                .body(response);
    }

    @GetMapping("/by-recurrence/{recurrenceId}")
    @Operation(summary = "Get all sessions in a recurrence")
    @ApiResponse(responseCode = "200", description = "Sessions retrieved successfully")
    @ApiResponse(responseCode = "404", description = "No sessions found for this recurrence ID")
    @ApiResponse(responseCode = "422", description = "Malformed UUID")
    public ResponseEntity<List<ClassSessionScheduleResponse>> getByRecurrenceId(@PathVariable @ValidUuid String recurrenceId) {
        return ResponseEntity.ok(service.getByRecurrenceId(UUID.fromString(recurrenceId)));
    }

    @PatchMapping("/{id}")
    @Operation(
        summary = "Edit a scheduled class session",
        description = """
            Patch an existing SCHEDULED session to change its time, duration, instructor, room, or capacity.

            Only SCHEDULED sessions can be edited. Sessions that have already started, or are COMPLETED/CANCELLED cannot be modified.

            Capacity changes are validated: new capacity cannot drop below the current booked count,
            and cannot exceed the room's maximum capacity.

            When capacity is increased, waiting list members are promoted automatically (atomically within the transaction).

            When time or resources (instructor/room) change, SESSION_RESCHEDULED notifications are written for all members with active bookings.

            classTypeId is immutable and will be rejected if supplied.
            """
    )
    @ApiResponse(responseCode = "200", description = "Session updated successfully")
    @ApiResponse(responseCode = "409", description = "Conflict or state violation (e.g. capacity, time, status, concurrency)")
    @ApiResponse(responseCode = "422", description = "Input validation failed or unknown field supplied")
    public ResponseEntity<PatchClassSessionResponse> patch(
            @PathVariable @ValidUuid String id,
            @Valid @RequestBody PatchClassSessionRequest request) {
        PatchClassSessionResponse response = service.patch(UUID.fromString(id), request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Cancel a scheduled class session",
        description = """
            Cancel a SCHEDULED session. Cascades to:
            - All BOOKED bookings become CANCELLED with cancellationType: SESSION_CANCELLED
            - All credits are refunded to members (no late-cancel window applies for session cancellation)
            - All WAITING waitlist entries become EXPIRED
            - One notification per affected member

            Bookings already CHECKED_IN or NO_SHOW are not affected.
            Sessions that have already started but not completed can be cancelled.
            """
    )
    @ApiResponse(responseCode = "200", description = "Session cancelled successfully")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @ApiResponse(responseCode = "409", description = "Conflict (e.g. session already cancelled, already completed)")
    @ApiResponse(responseCode = "422", description = "Input validation failed (e.g. reason exceeds 255 characters)")
    public ResponseEntity<CancelSessionResponse> cancel(
            @PathVariable @ValidUuid String id,
            @RequestBody(required = false) CancelSessionRequest request) {
        // Allow cancel with null request body (reason is optional)
        CancelSessionRequest cancelRequest = request != null ? request : new CancelSessionRequest(null);
        CancelSessionResponse response = service.cancel(UUID.fromString(id), cancelRequest);
        return ResponseEntity.ok(response);
    }
}
