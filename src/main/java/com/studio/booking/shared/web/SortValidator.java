package com.studio.booking.shared.web;

import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.error.FieldError;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Validates the {@code sort} parameter of {@link SortablePageParams}.
 *
 * <p>Usage:
 * <pre>
 *   sortValidator.validate(params, new String[]{"createdAt", "name"});  // sortable
 *   sortValidator.validateFixedOrder(params);                           // no sort allowed
 * </pre>
 *
 * Enforcement is declarative: callers pass the allow-list from the {@link AllowedSortFields}
 * annotation rather than comparing strings per-controller.
 */
@Component
public class SortValidator {

    /**
     * Validates that the sort field is within the given allow-list.
     * The allow-list is typically sourced from {@link AllowedSortFields#value()}.
     *
     * @param params     the params object whose sort field to validate
     * @param allowedFields the fields this endpoint permits
     */
    public void validate(SortablePageParams params, String[] allowedFields) {
        String field = params.sortField();
        if (field == null) {
            return; // sort omitted — always fine
        }

        Set<String> allowed = Set.of(allowedFields);
        if (!allowed.contains(field)) {
            String permitted = String.join(", ", allowedFields);
            throw ApiException.validationFailed(
                    "Request validation failed. See errors.",
                    List.of(FieldError.of(
                            "sort",
                            "INVALID_ENUM",
                            "Invalid sort field '" + field + "'. Permitted fields: " + permitted,
                            field)));
        }
    }

    /**
     * Rejects any non-null sort value for a fixed-order endpoint.
     * Call this on endpoints where the result order is not user-configurable.
     */
    public void validateFixedOrder(SortablePageParams params) {
        if (params.getSort() != null && !params.getSort().isBlank()) {
            throw ApiException.validationFailed(
                    "Request validation failed. See errors.",
                    List.of(FieldError.of(
                            "sort",
                            "NOT_SUPPORTED",
                            "This endpoint returns results in a fixed order; the sort parameter is not accepted.",
                            params.getSort())));
        }
    }
}
