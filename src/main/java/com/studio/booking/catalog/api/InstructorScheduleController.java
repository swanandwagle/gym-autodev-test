package com.studio.booking.catalog.api;

import com.studio.booking.catalog.api.response.ClassSessionScheduleResponse;
import com.studio.booking.catalog.application.InstructorScheduleService;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.validation.ValidUuid;
import com.studio.booking.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instructors/{instructorId}/schedule")
@Tag(name = "Instructor Schedule", description = "Instructor teaching schedule endpoints")
public class InstructorScheduleController {

    private final InstructorScheduleService service;
    private final Clock clock;

    public InstructorScheduleController(InstructorScheduleService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    @Operation(
            summary = "Get instructor teaching schedule",
            description = "Retrieve the instructor's teaching schedule for a date range. " +
                    "Default window is the next 7 days. " +
                    "The 'to' parameter is exclusive (half-open interval [from, to))."
    )
    @ApiResponse(responseCode = "200", description = "Schedule retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Instructor not found")
    @ApiResponse(responseCode = "422", description = "Invalid range or format")
    public ResponseEntity<PageResponse<ClassSessionScheduleResponse>> getSchedule(
            @PathVariable @ValidUuid String instructorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "Range start date (inclusive), format YYYY-MM-DD. Defaults to today.")
            LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "Range end date (exclusive), format YYYY-MM-DD. Defaults to 7 days from start. " +
                    "Note: unlike report endpoints, this parameter is exclusive.")
            LocalDate to,
            @RequestParam(required = false)
            @Parameter(description = "Session status filter: SCHEDULED (default), CANCELLED, or SCHEDULED,COMPLETED")
            String status,
            @RequestParam(required = false)
            @Parameter(hidden = true)
            String sort) {

        // AC-10: Reject sort parameter
        if (sort != null && !sort.isBlank()) {
            throw ApiException.validationFailed("Sorting is not supported on this endpoint", null);
        }

        // Parse instructorId
        UUID id = UUID.fromString(instructorId);

        // Set defaults for date range
        if (from == null) {
            LocalDate today = LocalDate.now(clock);
            from = today;
        }
        if (to == null) {
            to = from.plusDays(7);
        }

        // Validate range: AC-5, AC-6
        validateDateRange(from, to);

        // Convert to Instant (using UTC midnight)
        Instant fromInstant = from.atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant toInstant = to.atStartOfDay(ZoneId.of("UTC")).toInstant();

        PageResponse<ClassSessionScheduleResponse> response = service.getSchedule(id, fromInstant, toInstant, status);
        return ResponseEntity.ok(response);
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        // AC-5: to must be after from
        if (!to.isAfter(from)) {
            throw ApiException.validationFailed(
                    "'to' must be strictly after 'from'",
                    null
            );
        }

        // AC-6: span must not exceed 92 days
        long spanDays = java.time.temporal.ChronoUnit.DAYS.between(from, to);
        if (spanDays > 92) {
            throw ApiException.validationFailed(
                    "Date span exceeds the maximum of 92 days",
                    null
            );
        }
    }
}
