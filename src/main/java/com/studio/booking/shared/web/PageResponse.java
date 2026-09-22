package com.studio.booking.shared.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Standard page envelope returned by every paginated list endpoint.
 * Shape: { content: [...], page: { number, size, totalElements, totalPages } }
 */
@Schema(description = "Paginated response envelope")
public record PageResponse<T>(

        @Schema(description = "Page content")
        List<T> content,

        @Schema(description = "Pagination metadata")
        PageMetadata page

) {

    public record PageMetadata(

            @Schema(description = "Zero-based page number", example = "0")
            int number,

            @Schema(description = "Number of items per page", example = "20")
            int size,

            @Schema(description = "Total number of elements across all pages", example = "42")
            long totalElements,

            @Schema(description = "Total number of pages", example = "3")
            int totalPages

    ) {}

    public static <T> PageResponse<T> of(List<T> content, int pageNumber, int pageSize, long totalElements) {
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
        return new PageResponse<>(content, new PageMetadata(pageNumber, pageSize, totalElements, totalPages));
    }

    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> springPage) {
        return new PageResponse<>(
                springPage.getContent(),
                new PageMetadata(
                        springPage.getNumber(),
                        springPage.getSize(),
                        springPage.getTotalElements(),
                        springPage.getTotalPages()
                )
        );
    }
}
