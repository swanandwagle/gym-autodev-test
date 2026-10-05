package com.studio.booking.booking.api;

import com.studio.booking.booking.application.BookingService;
import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.web.PageParams;
import com.studio.booking.shared.web.PageParamsValidator;
import com.studio.booking.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members/{memberId}/bookings")
@Tag(name="Bookings", description="Booking retrieval and member history")
public class MemberBookingController {
    private final BookingService service;
    private final PageParamsValidator pageValidator;
    public MemberBookingController(BookingService service, PageParamsValidator pageValidator) {
        this.service = service; this.pageValidator = pageValidator;
    }

    @GetMapping
    @Operation(summary="List a member's bookings", description="Filters session start in the half-open interval [from,to). upcomingOnly selects future BOOKED bookings and cannot be combined with status.")
    public PageResponse<BookingResponse> history(@PathVariable String memberId,
            @RequestParam(required=false) String status, @RequestParam(required=false) String from,
            @RequestParam(required=false) String to, @RequestParam(defaultValue="false") boolean upcomingOnly,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @Parameter(description="Unsupported for member booking history") @RequestParam(required=false) String sort) {
        UUID member = uuid(memberId, "memberId");
        if (sort != null) throw new ApiException(ErrorCode.INVALID_SORT_FIELD, "sort is not supported for booking history");
        PageParams params = new PageParams(); params.setPage(page); params.setSize(size); pageValidator.validate(params);
        if (upcomingOnly && status != null) throw new ApiException(ErrorCode.VALIDATION_FAILED, "status cannot be combined with upcomingOnly");
        List<String> statuses = status == null ? null : Arrays.stream(status.split(",")).map(String::trim).toList();
        if (statuses != null && statuses.stream().anyMatch(s -> !List.of("BOOKED", "CANCELLED", "ATTENDED", "NO_SHOW").contains(s)))
            throw new ApiException(ErrorCode.INVALID_ENUM, "status contains an unsupported value");
        Instant start = instant(from, "from"), end = instant(to, "to");
        if (start != null && end != null && !end.isAfter(start)) throw new ApiException(ErrorCode.INVALID_RANGE, "to must be after from");
        Page<BookingResponse> pageResult = service.getMemberHistory(member, statuses, start, end, upcomingOnly, page, size)
            .map(BookingResponse::from);
        return PageResponse.of(pageResult);
    }
    private UUID uuid(String value, String field) {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException e) { throw new ApiException(ErrorCode.INVALID_FORMAT, field + " must be a UUID"); }
    }
    private Instant instant(String value, String field) {
        if (value == null) return null;
        try { return Instant.parse(value); }
        catch (DateTimeParseException e) { throw new ApiException(ErrorCode.INVALID_FORMAT, field + " must be an ISO-8601 instant"); }
    }
}
