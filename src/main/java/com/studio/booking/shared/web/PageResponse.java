package com.studio.booking.shared.web;

import java.util.List;

public record PageResponse<T>(List<T> content, PageMeta page) {

    public record PageMeta(int number, int size, long totalElements, int totalPages) {}

    public static <T> PageResponse<T> of(List<T> content, int number, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, new PageMeta(number, size, totalElements, totalPages));
    }
}
