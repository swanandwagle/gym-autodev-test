package com.studio.booking.shared.error;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Single entry point for all HTTP error responses.
 *
 * Every response uses the same {@link ErrorEnvelope} shape regardless of origin:
 *   1. Bean-validation failure      → 422 VALIDATION_FAILED  (includes errors[])
 *   2. {@link ApiException}          → status/code from the exception
 *   3. Database constraint violation → mapped code via {@link ConstraintViolationTranslator}
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

    private final ConstraintViolationTranslator constraintTranslator;

    public GlobalExceptionHandler(ConstraintViolationTranslator constraintTranslator) {
        this.constraintTranslator = constraintTranslator;
    }

    // -------------------------------------------------------------------------
    // 1. Bean-validation — MethodArgumentNotValidException (@Valid on controller)
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> FieldError.of(
                        fe.getField(),
                        mapSpringFieldErrorCode(fe),
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
    // 1c. HandlerMethodValidationException — Spring 6.1+ method parameter validation
    //     (path/query params annotated on @Validated controllers)
    // -------------------------------------------------------------------------

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorEnvelope> handleHandlerMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {

        List<FieldError> fieldErrors = ex.getAllValidationResults().stream()
                .flatMap(result -> {
                    String paramName = result.getMethodParameter().getParameterName();
                    if (paramName == null) {
                        paramName = "param" + result.getMethodParameter().getParameterIndex();
                    }
                    final String name = paramName;
                    Object rejected = result.getArgument();
                    return result.getResolvableErrors().stream()
                            .map(error -> {
                                String code = "INVALID";
                                if (error instanceof org.springframework.validation.FieldError fe) {
                                    code = mapSpringFieldErrorCode(fe);
                                } else {
                                    // Extract from codes array: first code is most-specific
                                    String[] codes = error.getCodes();
                                    if (codes != null && codes.length > 0) {
                                        // codes[0] is like "ValidUuid.methodName.paramName"
                                        // take the first segment before the first dot
                                        String first = codes[0];
                                        int dot = first.indexOf('.');
                                        String simpleName = dot > 0 ? first.substring(0, dot) : first;
                                        code = mapSimpleConstraintName(simpleName.toUpperCase());
                                    }
                                }
                                return FieldError.of(name, code, error.getDefaultMessage(), rejected);
                            });
                })
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

        ErrorCode code = constraintTranslator.translate(ex);
        return buildResponse(code, ErrorMessages.forCode(code), request, null, null);
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
    // 6. MethodArgumentTypeMismatchException — path/query variable type mismatch
    //    (e.g. malformed UUID in path variable → 422 INVALID_FORMAT)
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorEnvelope> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        List<FieldError> fieldErrors = List.of(FieldError.of(
                ex.getName(),
                "INVALID_FORMAT",
                "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue(),
                ex.getValue()));

        return buildResponse(ErrorCode.VALIDATION_FAILED,
                ErrorMessages.forCode(ErrorCode.VALIDATION_FAILED), request, null, fieldErrors);
    }

    // -------------------------------------------------------------------------
    // 7. Malformed JSON — UnrecognizedPropertyException → 422; other parse errors → 400
    // -------------------------------------------------------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        Throwable cause = ex.getCause();

        if (cause instanceof UnrecognizedPropertyException upe) {
            List<FieldError> fieldErrors = List.of(FieldError.of(
                    upe.getPropertyName(),
                    "UNKNOWN_FIELD",
                    "Unknown field: " + upe.getPropertyName(),
                    upe.getPropertyName()));
            return buildResponse(ErrorCode.VALIDATION_FAILED,
                    ErrorMessages.forCode(ErrorCode.VALIDATION_FAILED), request, null, fieldErrors);
        }

        if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null
                && ife.getTargetType().isEnum()) {
            String fieldName = ife.getPath().isEmpty() ? "unknown"
                    : ife.getPath().getLast().getFieldName();
            String[] accepted = Arrays.stream(ife.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .toArray(String[]::new);
            String message = "Invalid value. Accepted values: " + String.join(", ", accepted);
            List<FieldError> fieldErrors = List.of(FieldError.of(
                    fieldName,
                    "INVALID_ENUM",
                    message,
                    ife.getValue()));
            return buildResponse(ErrorCode.VALIDATION_FAILED,
                    ErrorMessages.forCode(ErrorCode.VALIDATION_FAILED), request, null, fieldErrors);
        }

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
        String raw = cv.getConstraintDescriptor()
                .getAnnotation()
                .annotationType()
                .getSimpleName()
                .toUpperCase();
        return switch (raw) {
            case "SIZE" -> {
                jakarta.validation.constraints.Size sizeAnn =
                        (jakarta.validation.constraints.Size) cv.getConstraintDescriptor().getAnnotation();
                Object val = cv.getInvalidValue();
                int len = val instanceof String s ? s.length()
                        : val instanceof java.util.Collection<?> c ? c.size()
                        : val instanceof Object[] a ? a.length : -1;
                yield len >= 0 && len < sizeAnn.min() ? "TOO_SHORT" : "TOO_LONG";
            }
            case "VALIDUUID" -> "INVALID_FORMAT";
            default -> raw;
        };
    }

    /** Maps a simple (annotation class name uppercased) constraint name to the API field code. */
    private static String mapSimpleConstraintName(String name) {
        return switch (name) {
            case "VALIDUUID" -> "INVALID_FORMAT";
            default -> name;
        };
    }

    /**
     * Maps a Spring {@link org.springframework.validation.FieldError} to the field code string
     * used in the error response. Handles @Size → TOO_SHORT / TOO_LONG by comparing the
     * rejected value length against the annotation's min/max via the FieldError's arguments.
     *
     * Spring populates arguments[] for @Size as: [0]=field-descriptor, [1]=max, [2]=min
     * (in reverse alphabetical order of annotation attribute names).
     */
    private static String mapSpringFieldErrorCode(org.springframework.validation.FieldError fe) {
        String code = fe.getCode();
        if (code == null) return "INVALID";
        String upper = code.toUpperCase();
        if ("SIZE".equals(upper)) {
            Object[] args = fe.getArguments();
            // Spring provides arguments in order: [descriptor, max, min]
            if (args != null && args.length >= 3) {
                try {
                    int max = ((Number) args[1]).intValue();
                    int min = ((Number) args[2]).intValue();
                    Object val = fe.getRejectedValue();
                    int len = val instanceof String s ? s.length()
                            : val instanceof java.util.Collection<?> c ? c.size()
                            : val instanceof Object[] a ? a.length : -1;
                    if (len >= 0) {
                        return len < min ? "TOO_SHORT" : "TOO_LONG";
                    }
                } catch (ClassCastException ignored) {
                    // fall through to default
                }
            }
        }
        return upper;
    }
}
