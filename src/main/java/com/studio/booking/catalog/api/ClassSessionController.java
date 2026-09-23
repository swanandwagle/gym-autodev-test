package com.studio.booking.catalog.api;

import com.studio.booking.catalog.api.request.CreateClassSessionRequest;
import com.studio.booking.catalog.api.response.ClassSessionScheduleResponse;
import com.studio.booking.catalog.application.ClassSessionService;
import com.studio.booking.shared.validation.ValidUuid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "Schedule and manage fitness class sessions")
public class ClassSessionController {

    private final ClassSessionService service;

    public ClassSessionController(ClassSessionService service) {
        this.service = service;
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
}
