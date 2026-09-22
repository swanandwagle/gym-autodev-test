package com.studio.booking.shared.idempotency;

import com.studio.booking.shared.error.ApiException;
import com.studio.booking.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link IdempotencyService}.
 *
 * Covers AC-1, AC-6, AC-7, AC-8 directly on the service.
 */
class IdempotencyServiceTest {

    private final IdempotencyService service = new IdempotencyService();

    private static final UUID MEMBER_A = UUID.randomUUID();
    private static final UUID MEMBER_B = UUID.randomUUID();
    private static final UUID SESSION_1 = UUID.randomUUID();
    private static final UUID SESSION_2 = UUID.randomUUID();

    // =========================================================================
    // AC-1: No key → validateKey is a no-op
    // =========================================================================

    @Test
    void testAc1_nullKeyIsNoOp() {
        assertThatCode(() -> service.validateKey(null)).doesNotThrowAnyException();
    }

    @Test
    void testAc1_blankKeyIsNoOp() {
        assertThatCode(() -> service.validateKey("")).doesNotThrowAnyException();
        assertThatCode(() -> service.validateKey("   ")).doesNotThrowAnyException();
    }

    @Test
    void testAc1_noKeyProceedIsReturned() {
        IdempotencyResult result = service.checkReplay(Optional.empty(), SESSION_1);
        assertThat(result.isReplay()).isFalse();
    }

    // =========================================================================
    // AC-8: Key longer than 64 characters → 422 VALIDATION_FAILED / INVALID_FORMAT
    // =========================================================================

    @Test
    void testAc8_keyExactly64CharsIsAccepted() {
        String key = "a".repeat(64);
        assertThatCode(() -> service.validateKey(key)).doesNotThrowAnyException();
    }

    @Test
    void testAc8_keyOver64CharsReturns422InvalidFormat() {
        String key = "a".repeat(65);
        ApiException ex = catchThrowableOfType(
                () -> service.validateKey(key), ApiException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(ex.getHttpStatus().value()).isEqualTo(422);
        assertThat(ex.getFieldErrors()).isNotEmpty();
        assertThat(ex.getFieldErrors().getFirst().code()).isEqualTo("INVALID_FORMAT");
        assertThat(ex.getFieldErrors().getFirst().field()).isEqualTo("Idempotency-Key");
    }

    @Test
    void testAc8_keyOf65CharsReturns422() {
        String key = "x".repeat(65);
        assertThatThrownBy(() -> service.validateKey(key))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                });
    }

    // =========================================================================
    // AC-6: Same key, different contextId (sessionId) → 409 IDEMPOTENCY_KEY_CONFLICT
    // =========================================================================

    @Test
    void testAc6_sameKeyDifferentContextIdReturns409Conflict() {
        IdempotencyRecord existing = new IdempotencyRecord(
                MEMBER_A, "key-abc", SESSION_1, 201, "{\"id\":\"...\"}");

        ApiException ex = catchThrowableOfType(
                () -> service.checkReplay(existing, SESSION_2), ApiException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        assertThat(ex.getHttpStatus().value()).isEqualTo(409);
    }

    @Test
    void testAc6_optionalOverloadDifferentContextIdReturns409() {
        IdempotencyRecord existing = new IdempotencyRecord(
                MEMBER_A, "key-abc", SESSION_1, 201, "{\"id\":\"...\"}");

        assertThatThrownBy(() -> service.checkReplay(Optional.of(existing), SESSION_2))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
    }

    // =========================================================================
    // AC-7: Same key, same contextId → replay result (different member not tested here;
    //       member isolation is enforced by the DB unique index on (member_id, key))
    // =========================================================================

    @Test
    void testAc7_sameKeyAndContextIdReturnsReplayResult() {
        String body = "{\"id\":\"booking-1\",\"status\":\"CONFIRMED\"}";
        IdempotencyRecord existing = new IdempotencyRecord(
                MEMBER_A, "key-abc", SESSION_1, 201, body);

        IdempotencyResult result = service.checkReplay(existing, SESSION_1);

        assertThat(result.isReplay()).isTrue();
        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(result.responseBody()).isEqualTo(body);
    }

    @Test
    void testAc7_optionalOverloadSameContextReturnsReplay() {
        IdempotencyRecord existing = new IdempotencyRecord(
                MEMBER_A, "key-abc", SESSION_1, 201, "{}");

        IdempotencyResult result = service.checkReplay(Optional.of(existing), SESSION_1);

        assertThat(result.isReplay()).isTrue();
    }

    // =========================================================================
    // AC-9: Replay of a cancelled booking returns the cancelled record
    //       (statusCode on the record reflects the original response,
    //        so a replay always returns whatever statusCode was stored)
    // =========================================================================

    @Test
    void testAc9_replayCancelledBookingReturnsStoredCancelledBody() {
        String cancelledBody = "{\"id\":\"booking-1\",\"status\":\"CANCELLED\"}";
        // After cancellation, the row still exists with the stored status=CANCELLED body
        IdempotencyRecord existing = new IdempotencyRecord(
                MEMBER_A, "key-abc", SESSION_1, 201, cancelledBody);

        IdempotencyResult result = service.checkReplay(existing, SESSION_1);

        assertThat(result.isReplay()).isTrue();
        // The replay returns the stored body as-is (which the booking service updated to CANCELLED)
        assertThat(result.responseBody()).isEqualTo(cancelledBody);
        assertThat(result.responseBody()).contains("CANCELLED");
    }

    // =========================================================================
    // IDEMPOTENCY_KEY_CONFLICT referenced for ErrorCodeCoverageTest
    // =========================================================================
    // ErrorCode.IDEMPOTENCY_KEY_CONFLICT is referenced in testAc6_sameKeyDifferentContextIdReturns409Conflict
}
