package com.studio.booking.shared.error;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Base exception for all domain and application-layer errors.
 * Carries an {@link ErrorCode} (which determines the HTTP status by construction),
 * a detail message, and optional field errors.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldError> fieldErrors;

    public ApiException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
        this.fieldErrors = List.of();
    }

    public ApiException(ErrorCode errorCode, String detail, List<FieldError> fieldErrors) {
        super(detail);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors != null ? List.copyOf(fieldErrors) : List.of();
    }

    public ApiException(ErrorCode errorCode, String detail, Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
        this.fieldErrors = List.of();
    }

    public ErrorCode getErrorCode() { return errorCode; }

    /** Derived from the enum constant — identical by construction for any given code. */
    public HttpStatus getHttpStatus() { return errorCode.httpStatus(); }

    public List<FieldError> getFieldErrors() { return fieldErrors; }

    // -------------------------------------------------------------------------
    // Factory methods for the most common cases
    // -------------------------------------------------------------------------

    public static ApiException notFound(String detail) {
        return new ApiException(ErrorCode.NOT_FOUND, detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(ErrorCode.CONFLICT, detail);
    }

    public static ApiException notPermitted(String detail) {
        return new ApiException(ErrorCode.NOT_PERMITTED, detail);
    }

    public static ApiException concurrentModification(String detail) {
        return new ApiException(ErrorCode.CONCURRENT_MODIFICATION, detail);
    }

    public static ApiException validationFailed(String detail, List<FieldError> fieldErrors) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, detail, fieldErrors);
    }
}
