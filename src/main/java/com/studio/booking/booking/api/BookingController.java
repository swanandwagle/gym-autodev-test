package com.studio.booking.booking.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.booking.application.BookingService;
import com.studio.booking.booking.domain.Booking;
import com.studio.booking.booking.infrastructure.BookingRepository;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.error.ErrorEnvelope;
import com.studio.booking.shared.idempotency.IdempotencyRecord;
import com.studio.booking.shared.idempotency.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings", description = "Booking creation and management")
public class BookingController {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public BookingController(
        BookingService bookingService,
        BookingRepository bookingRepository,
        IdempotencyService idempotencyService,
        ObjectMapper objectMapper
    ) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a booking", description = "Returns the full booking representation with session detail.")
    @ApiResponses({@ApiResponse(responseCode="200", description="Booking found"), @ApiResponse(responseCode="404", description="BOOKING_NOT_FOUND"), @ApiResponse(responseCode="422", description="Malformed UUID")})
    public BookingResponse getBooking(@PathVariable String id) {
        return BookingResponse.from(bookingService.getBooking(parseUuid(id, "id")));
    }

    private UUID parseUuid(String value, String field) {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ex) { throw new ApiException(ErrorCode.INVALID_FORMAT, field + " must be a UUID"); }
    }

    @PostMapping
    @Operation(
        summary = "Create a booking",
        description = "Creates a new booking for a member on a session. " +
                      "Returns 201 on success, 200 on idempotent replay. " +
                      "Rules: suspension checked first, credits checked before capacity. " +
                      "Supports optional Idempotency-Key header for safe retries."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Booking successfully created",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = BookingResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "200",
            description = "Idempotent replay of previous successful booking (no Location header)",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = BookingResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Member suspended or membership inactive",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Member or session not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorEnvelope.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Conflict: session full/cancelled/started, duplicate booking, no credits, overlapping booking, idempotency key mismatch",
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
    public ResponseEntity<BookingResponse> createBooking(
        @Valid @RequestBody CreateBookingRequest request,
        HttpServletRequest httpRequest
    ) {
        String idempotencyKey = httpRequest.getHeader("Idempotency-Key");
        idempotencyService.validateKey(idempotencyKey);

        UUID memberId = UUID.fromString(request.memberId());
        UUID sessionId = UUID.fromString(request.sessionId());

        // Check for idempotent replay
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Booking> existingBooking = bookingRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey);
            if (existingBooking.isPresent()) {
                Booking booking = existingBooking.get();
                IdempotencyRecord record = new IdempotencyRecord(
                    memberId,
                    idempotencyKey,
                    sessionId,
                    201,
                    booking.getIdempotencyResponseBody()
                );
                IdempotencyResult replay = idempotencyService.checkReplay(record, sessionId);
                if (replay.isReplay()) {
                    try {
                        BookingResponse bookingResponse = objectMapper.readValue(replay.responseBody(), BookingResponse.class);
                        return ResponseEntity.ok(bookingResponse);
                    } catch (JsonProcessingException e) {
                        throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to deserialize idempotent response", e);
                    }
                }
            }
        }

        String actorType = httpRequest.getHeader("X-Actor-Type");
        BookingService.BookingCreateResult result = bookingService.createBooking(memberId, sessionId, actorType);
        Booking booking = result.getBooking();

        BookingResponse response = BookingResponse.from(booking, result.isCreditDeducted());

        // Store idempotency key and serialized response on booking if provided
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                String responseBody = objectMapper.writeValueAsString(response);
                bookingRepository.updateIdempotencyFields(booking.getId(), idempotencyKey, responseBody);
            } catch (JsonProcessingException e) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to serialize booking response", e);
            }
        }

        URI location = URI.create("/api/v1/bookings/" + booking.getId());
        return ResponseEntity.created(location).body(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a booking", description = "Cancels a booked spot. A successful cancellation does not imply available capacity because a waitlisted member may be promoted immediately. Credits are refunded when cancellation is at least four hours before the session.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Booking cancelled"),
        @ApiResponse(responseCode = "404", description = "BOOKING_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "BOOKING_NOT_CANCELLABLE or SESSION_ALREADY_STARTED"),
        @ApiResponse(responseCode = "422", description = "Invalid booking ID or reason exceeds 255 characters")
    })
    public CancelBookingResponse cancelBooking(@PathVariable String id,
            @Valid @RequestBody(required = false) CancelBookingRequest request) {
        UUID bookingId = parseUuid(id, "id");
        BookingService.BookingCancellationResult result = bookingService.cancelBooking(bookingId);
        return new CancelBookingResponse(result.bookingId(), "CANCELLED", result.cancelledAt(),
            result.cancellationType(), result.creditRefunded(), result.promotedWaitlistEntryId());
    }
}
