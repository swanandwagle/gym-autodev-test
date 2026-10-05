package com.studio.booking.catalog.api.request;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;

import java.time.Instant;
import java.util.UUID;

@ParameterObject
@Schema(description = "Filter and pagination parameters for browsing upcoming fitness class sessions")
public class BrowseSessionsRequest {

    @Parameter(
        description = "ISO-8601 start of time range (inclusive). Defaults to now. "
                + "When both from and to are omitted, defaults to next 7 days.",
        example = "2026-09-24T10:00:00Z"
    )
    private Instant from;

    @Parameter(
        description = "ISO-8601 end of time range (exclusive). "
                + "Note: to is exclusive; a session starting exactly at to is not included.",
        example = "2026-10-01T10:00:00Z"
    )
    private Instant to;

    @Parameter(
        description = "Studio-local date (YYYY-MM-DD) as shorthand for the entire day. "
                + "Interpreted in studio.timezone config. Cannot be combined with from.",
        example = "2026-09-23"
    )
    private String date;

    @Parameter(
        description = "Filter by class type ID. Unknown IDs match nothing and return 200 with empty page.",
        example = "550e8400-e29b-41d4-a716-446655440000"
    )
    private UUID classTypeId;

    @Parameter(
        description = "Filter by instructor ID. Unknown IDs match nothing and return 200 with empty page.",
        example = "550e8400-e29b-41d4-a716-446655440001"
    )
    private UUID instructorId;

    @Parameter(
        description = "Filter by room ID. Unknown IDs match nothing and return 200 with empty page.",
        example = "550e8400-e29b-41d4-a716-446655440002"
    )
    private UUID roomId;

    @Parameter(
        description = "If true, exclude sessions where bookedCount == capacity. Default is false.",
        example = "true"
    )
    private Boolean availableOnly;

    @Parameter(
        description = "Filter by session status. Default is SCHEDULED.",
        example = "SCHEDULED"
    )
    private String status;

    @Parameter(
        description = "Zero-based page number. Default is 0.",
        example = "0"
    )
    private Integer page;

    @Parameter(
        description = "Number of results per page. Default is 20, max 100.",
        example = "20"
    )
    private Integer size;

    @Parameter(
        description = "Sort expression: field,direction (e.g. startsAt,asc). "
                + "Only startsAt is supported. Default is startsAt,asc.",
        example = "startsAt,asc"
    )
    private String sort;

    public BrowseSessionsRequest() {}

    public Instant getFrom() { return from; }
    public void setFrom(Instant from) { this.from = from; }

    public Instant getTo() { return to; }
    public void setTo(Instant to) { this.to = to; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public UUID getClassTypeId() { return classTypeId; }
    public void setClassTypeId(UUID classTypeId) { this.classTypeId = classTypeId; }

    public UUID getInstructorId() { return instructorId; }
    public void setInstructorId(UUID instructorId) { this.instructorId = instructorId; }

    public UUID getRoomId() { return roomId; }
    public void setRoomId(UUID roomId) { this.roomId = roomId; }

    public Boolean getAvailableOnly() { return availableOnly; }
    public void setAvailableOnly(Boolean availableOnly) { this.availableOnly = availableOnly; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getPage() { return page != null ? page : 0; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size != null ? size : 20; }
    public void setSize(Integer size) { this.size = size; }

    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }

    public String sortField() {
        if (sort == null || sort.isBlank()) return "startsAt";
        int comma = sort.indexOf(',');
        return comma > 0 ? sort.substring(0, comma).trim() : sort.trim();
    }

    public String sortDirection() {
        if (sort == null || sort.isBlank()) return "asc";
        int comma = sort.indexOf(',');
        return comma > 0 ? sort.substring(comma + 1).trim().toLowerCase() : "asc";
    }
}
