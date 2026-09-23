package com.studio.booking.catalog.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.studio.booking.catalog.domain.RecurrenceConflict;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateRecurringSessionsResponse(
    UUID recurrenceId,
    List<ClassSessionScheduleResponse> created,
    Integer skipped,
    List<RecurrenceConflict> conflicts
) {
}
