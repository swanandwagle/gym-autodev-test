package com.studio.booking.catalog.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CreateRecurringSessionsRequest(
    @NotNull(message = "fromDate is required")
    LocalDate fromDate,

    @NotNull(message = "toDate is required")
    LocalDate toDate,

    @NotEmpty(message = "daysOfWeek cannot be empty")
    List<String> daysOfWeek,

    @NotNull(message = "startTimeLocal is required")
    LocalTime startTimeLocal,

    @NotNull(message = "classTypeId is required")
    UUID classTypeId,

    @NotNull(message = "instructorId is required")
    UUID instructorId,

    @NotNull(message = "roomId is required")
    UUID roomId,

    Integer durationMinutes,
    Integer capacity,

    @NotNull(message = "onConflict is required")
    @JsonProperty("onConflict")
    String onConflict
) {
}
