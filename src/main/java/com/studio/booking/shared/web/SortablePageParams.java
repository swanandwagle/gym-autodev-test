package com.studio.booking.shared.web;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;

/**
 * Extends {@link PageParams} with an optional {@code sort} parameter.
 *
 * Sort syntax: {@code field,asc} or {@code field,desc}.
 * Allowed fields are enforced by {@link SortValidator} using the {@link AllowedSortFields}
 * annotation placed on the controller parameter — not by per-controller string comparisons.
 */
@ParameterObject
public class SortablePageParams extends PageParams {

    @Parameter(
            description = "Sort expression: field,asc or field,desc. "
                    + "Available fields vary by endpoint and are listed in each endpoint's description.",
            schema = @Schema(type = "string", example = "createdAt,desc")
    )
    private String sort;

    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }

    /** Returns the field part of the sort expression (before the comma), or null if sort is absent. */
    public String sortField() {
        if (sort == null || sort.isBlank()) return null;
        int comma = sort.indexOf(',');
        return comma > 0 ? sort.substring(0, comma).trim() : sort.trim();
    }

    /** Returns the direction part (after the comma), defaulting to "asc" if absent. */
    public String sortDirection() {
        if (sort == null || sort.isBlank()) return "asc";
        int comma = sort.indexOf(',');
        return comma > 0 ? sort.substring(comma + 1).trim().toLowerCase() : "asc";
    }
}
