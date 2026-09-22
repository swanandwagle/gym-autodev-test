package com.studio.booking.shared.error;

import com.studio.booking.shared.web.CorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Single entry point for all HTTP error responses.
 *
 * Every response uses the same {@link ErrorEnvelope} shape regardless of origin:
 *   1. Bean-validation failure      → 422 VALIDATION_FAILED  (includes errors[])
 *   2. {@link ApiException}          → status/code from the exception
 *   3. Database constraint violation → 409 CONFLICT
 *   4. Optimistic-lock failure       → 409 CONCURRENT_MODIFICATION
 *   5. Unhandled runtime exception   → 500 INTERNAL_ERROR     (no internal detail)
 *
 * Malformed JSON ({@link HttpMessageNotReadableException}) → 400 MALFORMED_REQUEST.
 *
 * Detail messages are resolved from {@code error-messages.properties} via
 * {@link ErrorMessages} so wording changes do not require recompilation.
 *
 * Correlation: traceId is read from the request attribute set by {@link CorrelationFilter}.
 * The full exception is logged at ERROR with the traceId so it can be correlated server-side.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Value("${studio.api.base-url:https://api.studio.example}")
    private String baseUrl;

    // -------------------------------------------------------------------------
    // 1. Bean-validation — MethodArgumentNotValidException (@Valid on controller)
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> FieldError.of(
                        fe.getField(),
                        fe.getCode() != null ? fe.getCode().toUpperCase() : "INVALID",
                        fe.getDefaultMessage(),
                        fe.getRejectedValue()))
                .collect(Collectors.toList());

        return buildResponse(ErrorCode.VALIDATION_FAILED,
                ErrorMessages.forCode(ErrorCode.VALIDATION_FAILED), request, null, fieldErrors);
    }

    // -------------------------------------------------------------------------
    // 1b. Bean-validation — ConstraintViolationException (path/query params)
    // -------------------------------------------------------------------------

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(cv -> FieldError.of(
                        leafPath(cv),
                        constraintCode(cv),
                        cv.getMessage(),
                        cv.getInvalidValue()))
                .collect(Collectors.toList());

        return buildResponse(ErrorCode.VALIDATION_FAILED,
                ErrorMessages.forCode(ErrorCode.VALIDATION_FAILED), request, null, fieldErrors);
    }

    // -------------------------------------------------------------------------
    // 2. ApiException
    // -------------------------------------------------------------------------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorEnvelope> handleApiException(ApiException ex, HttpServletRequest request) {
        List<FieldError> errors = ex.getHttpStatus() == HttpStatus.UNPROCESSABLE_ENTITY
                ? ex.getFieldErrors()
                : null;
        return buildResponse(ex.getErrorCode(), ex.getMessage(), request, null, errors);
    }

    // -------------------------------------------------------------------------
    // 3. Database constraint violation
    // -------------------------------------------------------------------------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        String traceId = traceId(request);
        log.error("[traceId={}] DataIntegrityViolationException: {}", traceId, ex.getMessage(), ex);
        return buildResponse(ErrorCode.CONFLICT,
                ErrorMessages.forCode(ErrorCode.CONFLICT), request, null, null);
    }

    // -------------------------------------------------------------------------
    // 4. Optimistic-lock failure
    // -------------------------------------------------------------------------

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorEnvelope> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest request) {

        String traceId = traceId(request);
        log.error("[traceId={}] OptimisticLockingFailureException: {}", traceId, ex.getMessage(), ex);
        return buildResponse(ErrorCode.CONCURRENT_MODIFICATION,
                ErrorMessages.forCode(ErrorCode.CONCURRENT_MODIFICATION), request, null, null);
    }

    // -------------------------------------------------------------------------
    // 9. Malformed JSON
    // -------------------------------------------------------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        return buildResponse(ErrorCode.MALFORMED_REQUEST,
                ErrorMessages.forCode(ErrorCode.MALFORMED_REQUEST), request, null, null);
    }

    // -------------------------------------------------------------------------
    // 5. Catch-all — unhandled exceptions
    // -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = traceId(request);
        log.error("[traceId={}] Unhandled exception", traceId, ex);
        return buildResponse(ErrorCode.INTERNAL_ERROR,
                ErrorMessages.forCode(ErrorCode.INTERNAL_ERROR), request, null, null);
    }

    // -------------------------------------------------------------------------
    // Shared builder
    // -------------------------------------------------------------------------

    private ResponseEntity<ErrorEnvelope> buildResponse(ErrorCode code,
                                                         String detail,
                                                         HttpServletRequest request,
                                                         Exception loggableEx,
                                                         List<FieldError> errors) {
        String traceId = traceId(request);
        HttpStatus status = code.httpStatus();

        if (loggableEx != null) {
            log.error("[traceId={}] {}: {}", traceId, code, detail, loggableEx);
        }

        ErrorEnvelope envelope = ErrorEnvelope.builder()
                .type(typeUri(code))
                .title(titleFor(code))
                .status(status.value())
                .code(code)
                .detail(detail)
                .instance(request.getRequestURI())
                .timestamp(Instant.now())
                .traceId(traceId)
                .errors(errors)
                .build();

        return ResponseEntity.status(status).body(envelope);
    }

    private String traceId(HttpServletRequest request) {
        Object attr = request.getAttribute(CorrelationFilter.REQUEST_ATTR);
        return attr instanceof String s ? s : "unknown";
    }

    private String typeUri(ErrorCode code) {
        return baseUrl + "/errors/" + code.toKebab();
    }

    private static String titleFor(ErrorCode code) {
        return switch (code) {
            case MALFORMED_REQUEST          -> "Malformed Request";
            case NOT_PERMITTED              -> "Not Permitted";
            case MEMBER_SUSPENDED           -> "Member Suspended";
            case MEMBER_INACTIVE            -> "Member Inactive";
            case NOT_FOUND                  -> "Not Found";
            case MEMBER_NOT_FOUND           -> "Member Not Found";
            case MEMBERSHIP_PLAN_NOT_FOUND  -> "Membership Plan Not Found";
            case MEMBERSHIP_NOT_FOUND       -> "Membership Not Found";
            case CLASS_TYPE_NOT_FOUND       -> "Class Type Not Found";
            case INSTRUCTOR_NOT_FOUND       -> "Instructor Not Found";
            case ROOM_NOT_FOUND             -> "Room Not Found";
            case SESSION_NOT_FOUND          -> "Session Not Found";
            case BOOKING_NOT_FOUND          -> "Booking Not Found";
            case WAITLIST_ENTRY_NOT_FOUND   -> "Waitlist Entry Not Found";
            case CONFLICT                   -> "Conflict";
            case CONCURRENT_MODIFICATION    -> "Concurrent Modification";
            case DUPLICATE_BOOKING          -> "Duplicate Booking";
            case SESSION_FULL               -> "Session Full";
            case SESSION_CANCELLED          -> "Session Cancelled";
            case SESSION_NOT_BOOKABLE       -> "Session Not Bookable";
            case BOOKING_ALREADY_CANCELLED  -> "Booking Already Cancelled";
            case BOOKING_ALREADY_CHECKED_IN -> "Booking Already Checked In";
            case CHECK_IN_WINDOW_NOT_OPEN   -> "Check-In Window Not Open";
            case WAITLIST_ALREADY_JOINED    -> "Waitlist Already Joined";
            case WAITLIST_ALREADY_PROCESSED -> "Waitlist Already Processed";
            case MEMBERSHIP_ALREADY_ACTIVE  -> "Membership Already Active";
            case CREDITS_INSUFFICIENT       -> "Credits Insufficient";
            case OVERLAPPING_BOOKING        -> "Overlapping Booking";
            case LATE_CANCEL_NO_REFUND      -> "Late Cancel — No Refund";
            case INSTRUCTOR_SCHEDULE_CONFLICT -> "Instructor Schedule Conflict";
            case ROOM_SCHEDULE_CONFLICT     -> "Room Schedule Conflict";
            case IDEMPOTENCY_KEY_CONFLICT   -> "Idempotency Key Conflict";
            case JOB_ALREADY_RUNNING        -> "Job Already Running";
            case VALIDATION_FAILED          -> "Validation Failed";
            case INVALID_DATE_RANGE         -> "Invalid Date Range";
            case INVALID_PAGINATION         -> "Invalid Pagination";
            case INVALID_SORT_FIELD         -> "Invalid Sort Field";
            case UNKNOWN_FIELD              -> "Unknown Field";
            case INTERNAL_ERROR             -> "Internal Server Error";
        };
    }

    private static String leafPath(ConstraintViolation<?> cv) {
        String path = cv.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private static String constraintCode(ConstraintViolation<?> cv) {
        return cv.getConstraintDescriptor()
                .getAnnotation()
                .annotationType()
                .getSimpleName()
                .toUpperCase();
    }
}
