package com.studio.booking.shared.web;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Shared query parameters for endpoints that accept a date range ({@code from}/{@code to})
 * or a single {@code date}.
 *
 * Validation rules (enforced by {@link DateRangeValidator}):
 * <ul>
 *   <li>{@code date} is mutually exclusive with {@code from} and {@code to}.</li>
 *   <li>{@code to} must be strictly after {@code from} (exclusive upper bound).</li>
 *   <li>The span {@code [from, to)} must not exceed the endpoint's cap (days).</li>
 * </ul>
 *
 * The {@code to} bound is <strong>exclusive</strong>: a query with
 * {@code from=2026-01-01&to=2026-02-01} covers January only, not February 1st.
 */
@ParameterObject
public class DateRangeParams {

    @Parameter(
            description = "Range start date (inclusive), format YYYY-MM-DD. "
                    + "Mutually exclusive with 'date'.",
            schema = @Schema(type = "string", format = "date", example = "2026-01-01")
    )
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @Parameter(
            description = "Range end date (exclusive), format YYYY-MM-DD. "
                    + "Must be strictly after 'from'. "
                    + "Mutually exclusive with 'date'. "
                    + "Maximum span is endpoint-specific.",
            schema = @Schema(type = "string", format = "date", example = "2026-02-01")
    )
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    @Parameter(
            description = "Single date filter, format YYYY-MM-DD. "
                    + "Mutually exclusive with 'from' and 'to'.",
            schema = @Schema(type = "string", format = "date", example = "2026-01-15")
    )
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }

    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
}
