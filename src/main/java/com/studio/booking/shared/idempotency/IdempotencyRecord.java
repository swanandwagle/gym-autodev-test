package com.studio.booking.shared.idempotency;

import java.util.UUID;

/**
 * Carries the fields needed by {@link IdempotencyService} to evaluate a replay.
 *
 * <p>Consuming services (booking, waitlist) project their domain row into this record
 * before calling {@link IdempotencyService#checkReplay}.
 *
 * @param memberId         owner of the original request
 * @param idempotencyKey   the key submitted with the original request
 * @param contextId        discriminator that must match on a replay (e.g. sessionId);
 *                         a mismatch means the same key was reused with a different payload
 * @param statusCode       HTTP status code that was returned for the original request
 * @param responseBody     serialised JSON body returned for the original request
 */
public record IdempotencyRecord(
        UUID memberId,
        String idempotencyKey,
        UUID contextId,
        int statusCode,
        String responseBody
) {}
