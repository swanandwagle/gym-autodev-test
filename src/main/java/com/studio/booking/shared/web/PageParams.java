package com.studio.booking.shared.web;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;

/**
 * Shared query parameters for all paginated list endpoints.
 * Bind with {@code @ParameterObject PageParams params} on controller methods.
 *
 * Validation is performed by {@link PageParamsValidator} — call it explicitly in
 * the controller before using the params. This keeps the bean a plain data holder
 * and lets validation produce proper RFC-9457 field errors.
 */
@ParameterObject
public class PageParams {

    public static final int DEFAULT_SIZE = 20;
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 100;
    public static final int MIN_PAGE = 0;

    @Parameter(
            description = "Zero-based page number (default: 0)",
            schema = @Schema(type = "integer", minimum = "0", defaultValue = "0", example = "0")
    )
    private int page = 0;

    @Parameter(
            description = "Number of items per page (default: 20, max: 100)",
            schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20", example = "20")
    )
    private int size = DEFAULT_SIZE;

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
