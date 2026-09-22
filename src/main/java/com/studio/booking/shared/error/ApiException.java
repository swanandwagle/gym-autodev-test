package com.studio.booking.shared.error;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Base exception for all domain and application-layer errors.
 * Carries an {@link ErrorCode}, HTTP status, detail message, and optional field errors.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;
    private final List<FieldError> fieldErrors;

    public ApiException(ErrorCode errorCode, HttpStatus httpStatus, String detail) {
        super(detail);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.fieldErrors = List.of();
    }

    public ApiException(ErrorCode errorCode, HttpStatus httpStatus, String detail, List<FieldError> fieldErrors) {
        super(detail);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.fieldErrors = fieldErrors != null ? List.copyOf(fieldErrors) : List.of();
    }

    public ApiException(ErrorCode errorCode, HttpStatus httpStatus, String detail, Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.fieldErrors = List.of();
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public HttpStatus getHttpStatus() { return httpStatus; }
    public List<FieldError> getFieldErrors() { return fieldErrors; }

    // -------------------------------------------------------------------------
    // Factory methods for the most common cases
    // -------------------------------------------------------------------------

    public static ApiException notFound(String detail) {
        return new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, detail);
    }

    public static ApiException notPermitted(String detail) {
        return new ApiException(ErrorCode.NOT_PERMITTED, HttpStatus.FORBIDDEN, detail);
    }

    public static ApiException concurrentModification(String detail) {
        return new ApiException(ErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT, detail);
    }

    public static ApiException validationFailed(String detail, List<FieldError> fieldErrors) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, detail, fieldErrors);
    }
}
