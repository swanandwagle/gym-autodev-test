package com.studio.booking.shared.web;

import java.lang.annotation.*;

/**
 * Declares the sort fields permitted on a given list endpoint.
 * Placed on the {@code sort} parameter of a controller method.
 * If {@link #value()} is empty the parameter binding will reject any sort value.
 */
@Documented
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedSortFields {

    /**
     * The field names (without direction suffix) that callers may sort by.
     * Empty means the endpoint has a fixed order and rejects all sort input.
     */
    String[] value() default {};
}
