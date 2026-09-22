package com.studio.booking.shared.idempotency;

import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.error.FieldError;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Reusable idempotency framework for booking and waitlist creation.
 *
 * <h3>Contract</h3>
 * <ol>
 *   <li>Call {@link #validateKey(String)} at the start of any endpoint that accepts
 *       {@code Idempotency-Key}.  This rejects keys over 64 characters with
 *       {@code 422 VALIDATION_FAILED / INVALID_FORMAT} and is a no-op for absent keys.</li>
 *   <li>If a key is present, query the domain row by {@code (memberId, idempotencyKey)}.
 *       If a prior row exists, project it to {@link IdempotencyRecord} and call
 *       {@link #checkReplay(IdempotencyRecord, java.util.UUID)}.
 *       <ul>
 *         <li>If {@link IdempotencyResult#isReplay()} is true, return the stored response with
 *             HTTP 200 and <em>no</em> {@code Location} header.</li>
 *         <li>If the method throws, propagate the exception (mismatch → 409).</li>
 *       </ul>
 *   </li>
 *   <li>If no prior row exists (or key is absent), proceed with creation normally and
 *       persist the key on the new row.</li>
 * </ol>
 *
 * <h3>OpenAPI usage</h3>
 * Both consuming endpoints ({@code POST /api/v1/bookings} and
 * {@code POST /api/v1/sessions/{sessionId}/waitlist}) document the header as:
 * <pre>
 *   name: Idempotency-Key
 *   in: header
 *   required: false
 *   schema:
 *     type: string
 *     maxLength: 64
 *   description: >
 *     Optional client-supplied key (≤64 chars). Same member + same key → HTTP 200 replay of
 *     original response. Same key + different sessionId → 409 BOOKING_IDEMPOTENCY_CONFLICT.
 * </pre>
 */
@Service
public class IdempotencyService {

    static final int MAX_KEY_LENGTH = 64;

    /**
     * Validates the key length.  Must be called before any database work.
     *
     * @param key may be null or blank — those are treated as "no key supplied" (no-op)
     * @throws ApiException 422 VALIDATION_FAILED when key is non-blank and longer than 64 chars
     */
    public void validateKey(String key) {
        if (key != null && !key.isBlank() && key.length() > MAX_KEY_LENGTH) {
            throw ApiException.validationFailed(
                    "Request validation failed. See errors.",
                    List.of(FieldError.of(
                            "Idempotency-Key",
                            "INVALID_FORMAT",
                            "Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters.")));
        }
    }

    /**
     * Evaluates a prior row against the new request's context.
     *
     * @param existing    projection of the prior domain row
     * @param newContextId context discriminator from the current request (e.g. sessionId)
     * @return {@link IdempotencyResult#replay} when the context matches — caller must return 200
     * @throws ApiException 409 BOOKING_IDEMPOTENCY_CONFLICT when contextId differs
     */
    public IdempotencyResult checkReplay(IdempotencyRecord existing, java.util.UUID newContextId) {
        if (!existing.contextId().equals(newContextId)) {
            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                    "The idempotency key was previously used with different request parameters.");
        }
        return IdempotencyResult.replay(existing.statusCode(), existing.responseBody());
    }

    /**
     * Convenience overload: evaluates an {@link Optional} prior record.
     * Returns {@link IdempotencyResult#proceed()} when the optional is empty.
     */
    public IdempotencyResult checkReplay(Optional<IdempotencyRecord> existing, java.util.UUID newContextId) {
        return existing.map(r -> checkReplay(r, newContextId))
                       .orElseGet(IdempotencyResult::proceed);
    }
}
