package com.studio.booking.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.booking.shared.error.ErrorCode;
import com.studio.booking.shared.error.ErrorEnvelope;
import com.studio.booking.shared.error.GlobalExceptionHandler;
import com.studio.booking.shared.web.CorrelationFilter;
import com.studio.booking.shared.web.StrictJsonConfig;
import com.studio.booking.shared.error.ConstraintViolationTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the idempotency framework.
 *
 * Uses a stub booking controller backed by in-memory state to verify the full
 * request-handling contract without a real database. The stub faithfully implements
 * the pattern that real booking/waitlist services will follow:
 *   1. validateKey → 422 if too long
 *   2. atomic store.compute → replay if found, create if absent
 *   3. first create returns 201 + Location; replay returns 200, no Location
 *
 * Covers AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7, AC-8, AC-9, AC-10.
 */
@WebMvcTest(
        controllers = IdempotencyIntegrationTest.StubBookingController.class,
        excludeAutoConfiguration = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                FlywayAutoConfiguration.class
        }
)
@Import({
        GlobalExceptionHandler.class,
        CorrelationFilter.class,
        StrictJsonConfig.class,
        ConstraintViolationTranslator.class,
        IdempotencyService.class
})
@TestPropertySource(properties = "studio.api.base-url=https://api.studio.example")
class IdempotencyIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StubBookingController stubController;

    @BeforeEach
    void reset() {
        stubController.reset();
    }

    // =========================================================================
    // Stub controller that simulates the booking service idempotency pattern.
    //
    // Uses ConcurrentHashMap.compute for atomic check-and-insert — this is the
    // in-memory analogue of what the DB unique index on (member_id, idempotency_key)
    // provides in production.
    // =========================================================================

    @RestController
    @RequestMapping("/test/bookings")
    static class StubBookingController {

        @Autowired IdempotencyService idempotencyService;

        record BookingRequest(String memberId, String sessionId) {}

        record BookingResponse(String id, String memberId, String sessionId, String status) {}

        /** In-memory idempotency store: key = "memberId:idempotencyKey" */
        private final ConcurrentHashMap<String, IdempotencyRecord> store = new ConcurrentHashMap<>();

        final AtomicInteger creationCount       = new AtomicInteger(0);
        final AtomicInteger creditDeductions    = new AtomicInteger(0);
        final AtomicInteger bookedCountIncrements = new AtomicInteger(0);

        void reset() {
            store.clear();
            creationCount.set(0);
            creditDeductions.set(0);
            bookedCountIncrements.set(0);
        }

        @PostMapping
        ResponseEntity<BookingResponse> createBooking(
                @RequestBody BookingRequest req,
                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                @RequestHeader(value = "X-Member-Id", required = false,
                               defaultValue = "00000000-0000-0000-0000-000000000001") String memberIdStr) {

            UUID memberId  = UUID.fromString(memberIdStr);
            UUID sessionId = UUID.fromString(req.sessionId());

            // Step 1: validate key length — throws 422 if > 64 chars
            idempotencyService.validateKey(idempotencyKey);

            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                // No idempotency key — normal creation, no idempotency checks
                return doCreate(memberIdStr, req, sessionId, null);
            }

            // Step 2: atomic check-and-insert (mirrors DB unique index enforcement).
            // compute() is called once; if a record already exists the lambda returns it
            // unchanged, giving us the existing record.  If absent, the lambda performs
            // creation and stores the new record.
            String storeKey = memberIdStr + ":" + idempotencyKey;

            // We need to know whether this was a creation or a replay after compute().
            // Use a one-element array as a mutable carrier inside the lambda.
            boolean[] wasCreated = {false};

            IdempotencyRecord record = store.compute(storeKey, (k, existing) -> {
                if (existing != null) {
                    return existing; // replay — return existing unchanged
                }
                // Creation path: perform the side effects and build the record
                String bookingId = UUID.randomUUID().toString();
                creationCount.incrementAndGet();
                creditDeductions.incrementAndGet();
                bookedCountIncrements.incrementAndGet();

                BookingResponse response = new BookingResponse(
                        bookingId, memberIdStr, req.sessionId(), "CONFIRMED");
                String bodyJson = toJson(response);
                wasCreated[0] = true;
                return new IdempotencyRecord(memberId, idempotencyKey, sessionId, 201, bodyJson);
            });

            // Step 3: evaluate context mismatch — throws 409 if sessionId differs
            IdempotencyResult result = idempotencyService.checkReplay(record, sessionId);

            if (!wasCreated[0] && result.isReplay()) {
                // AC-10: replay returns 200 with no Location header
                return ResponseEntity.ok(parseResponse(result.responseBody()));
            }

            // First creation: return 201 with Location header
            return ResponseEntity.created(URI.create("/api/v1/bookings/" + parseResponse(record.responseBody()).id()))
                    .body(parseResponse(record.responseBody()));
        }

        /** Simulates post-creation cancellation by updating the stored response body. */
        void cancelBooking(String memberIdStr, String idempotencyKey, String sessionIdStr) {
            String storeKey = memberIdStr + ":" + idempotencyKey;
            store.computeIfPresent(storeKey, (k, existing) -> {
                BookingResponse cancelled = new BookingResponse(
                        "cancelled-booking", memberIdStr, sessionIdStr, "CANCELLED");
                return new IdempotencyRecord(
                        existing.memberId(), existing.idempotencyKey(), existing.contextId(),
                        existing.statusCode(), toJson(cancelled));
            });
        }

        private ResponseEntity<BookingResponse> doCreate(
                String memberIdStr, BookingRequest req, UUID sessionId, String idempotencyKey) {
            String bookingId = UUID.randomUUID().toString();
            creationCount.incrementAndGet();
            creditDeductions.incrementAndGet();
            bookedCountIncrements.incrementAndGet();
            BookingResponse response = new BookingResponse(
                    bookingId, memberIdStr, req.sessionId(), "CONFIRMED");
            return ResponseEntity.created(URI.create("/api/v1/bookings/" + bookingId))
                    .body(response);
        }

        private BookingResponse parseResponse(String json) {
            try {
                return new ObjectMapper().readValue(json, BookingResponse.class);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse stored response", e);
            }
        }

        private String toJson(Object obj) {
            try {
                return new ObjectMapper().writeValueAsString(obj);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialise response", e);
            }
        }
    }

    private static final String MEMBER_A  = "00000000-0000-0000-0000-000000000001";
    private static final String MEMBER_B  = "00000000-0000-0000-0000-000000000002";
    private static final String SESSION_1 = "10000000-0000-0000-0000-000000000001";
    private static final String SESSION_2 = "10000000-0000-0000-0000-000000000002";

    // =========================================================================
    // AC-1: No key → creation runs normally
    // =========================================================================

    @Test
    void testAc1_noIdempotencyKeyBehavesNormally() throws Exception {
        mockMvc.perform(post("/test/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Member-Id", MEMBER_A)
                        .content("{\"memberId\":\"" + MEMBER_A + "\",\"sessionId\":\"" + SESSION_1 + "\"}"))
                .andExpect(status().isCreated());

        assertThat(stubController.creationCount.get()).isEqualTo(1);
    }

    // =========================================================================
    // AC-2: Sequential replay — first 201, second 200 with identical body
    // =========================================================================

    @Test
    void testAc2_sequentialReplayFirstCreatedSecondReplayed() throws Exception {
        String body = "{\"memberId\":\"" + MEMBER_A + "\",\"sessionId\":\"" + SESSION_1 + "\"}";

        MvcResult first = mockMvc.perform(post("/test/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-seq-1")
                        .header("X-Member-Id", MEMBER_A)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/test/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-seq-1")
                        .header("X-Member-Id", MEMBER_A)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(first.getResponse().getContentAsString())
                .isEqualTo(second.getResponse().getContentAsString());
    }

    // =========================================================================
    // AC-3: Credit ledger deducted exactly once
    // =========================================================================

    @Test
    void testAc3_creditDeductedExactlyOnce() throws Exception {
        String body = "{\"memberId\":\"" + MEMBER_A + "\",\"sessionId\":\"" + SESSION_1 + "\"}";

        mockMvc.perform(post("/test/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-credit-1")
                        .header("X-Member-Id", MEMBER_A).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/test/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-credit-1")
                        .header("X-Member-Id", MEMBER_A).content(body))
                .andExpect(status().isOk());

        assertThat(stubController.creditDeductions.get())
                .as("Credit deducted exactly once across both requests")
                .isEqualTo(1);
    }

    // =========================================================================
    // AC-4: Session booked_count incremented exactly once
    // =========================================================================

    @Test
    void testAc4_bookedCountIncrementedExactlyOnce() throws Exception {
        String body = "{\"memberId\":\"" + MEMBER_A + "\",\"sessionId\":\"" + SESSION_1 + "\"}";

        mockMvc.perform(post("/test/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-booked-1")
                        .header("X-Member-Id", MEMBER_A).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/test/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-booked-1")
                        .header("X-Member-Id", MEMBER_A).content(body))
                .andExpect(status().isOk());

        assertThat(stubController.bookedCountIncrements.get())
                .as("booked_count incremented exactly once")
                .isEqualTo(1);
    }

    // =========================================================================
    // AC-5: Concurrent replay — genuinely parallel threads via ExecutorService
    //       One 201, rest 200; creation happens exactly once
    // =========================================================================

    @Test
    void testAc5_concurrentReplayProducesOneCreationBothSucceed() throws Exception {
        String requestBody = "{\"memberId\":\"" + MEMBER_A + "\",\"sessionId\":\"" + SESSION_1 + "\"}";
        String idempotencyKey = "key-concurrent-" + UUID.randomUUID();

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        // Barrier ensures all threads enter MockMvc.perform() simultaneously
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Future<MvcResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/test/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", idempotencyKey)
                                .header("X-Member-Id", MEMBER_A)
                                .content(requestBody))
                        .andReturn();
            }));
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        List<MvcResult> results = new ArrayList<>();
        for (Future<MvcResult> f : futures) {
            results.add(f.get());
        }

        // All responses must be success (no 4xx or 5xx)
        results.forEach(r -> assertThat(r.getResponse().getStatus())
                .as("All concurrent requests must succeed (200 or 201)")
                .isIn(200, 201));

        long created  = results.stream().filter(r -> r.getResponse().getStatus() == 201).count();
        long replayed = results.stream().filter(r -> r.getResponse().getStatus() == 200).count();

        assertThat(created)
                .as("Exactly one 201 across all %d concurrent requests", threadCount)
                .isEqualTo(1);
        assertThat(replayed)
                .as("Remaining %d requests must be 200 replays", threadCount - 1)
                .isEqualTo(threadCount - 1);

        assertThat(stubController.creationCount.get())
                .as("Underlying creation invoked exactly once")
                .isEqualTo(1);
    }

    // =========================================================================
    // AC-6: Same key, different sessionId → 409 BOOKING_IDEMPOTENCY_CONFLICT
    // =========================================================================

    @Test
    void testAc6_sameKeyDifferentSessionIdReturns409Conflict() throws Exception {
        mockMvc.perform(post("/test/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-mismatch")
                        .header("X-Member-Id", MEMBER_A)
                        .content("{\"memberId\":\"" + MEMBER_A + "\",\"sessionId\":\"" + SESSION_1 + "\"}"))
                .andExpect(status().isCreated());

        MvcResult conflict = mockMvc.perform(post("/test/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-mismatch")
                        .header("X-Member-Id", MEMBER_A)
                        .content("{\"memberId\":\"" + MEMBER_A + "\",\"sessionId\":\"" + SESSION_2 + "\"}"))
                .andExpect(status().isConflict())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                conflict.getResponse().getContentAsString(), ErrorEnvelope.class);
        assertThat(env.code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        assertThat(env.status()).isEqualTo(409);
    }

    // =========================================================================
    // AC-7: Same key, different member → separate bookings (both 201)
    // =========================================================================

    @Test
    void testAc7_sameKeyDifferentMemberCreatesNewBooking() throws Exception {
        String body = "{\"memberId\":\"x\",\"sessionId\":\"" + SESSION_1 + "\"}";

        MvcResult resultA = mockMvc.perform(post("/test/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-shared")
                        .header("X-Member-Id", MEMBER_A)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult resultB = mockMvc.perform(post("/test/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-shared")
                        .header("X-Member-Id", MEMBER_B)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(stubController.creationCount.get())
                .as("Two independent creations for two different members")
                .isEqualTo(2);

        // Different members produce different booking IDs
        assertThat(resultA.getResponse().getContentAsString())
                .isNotEqualTo(resultB.getResponse().getContentAsString());
    }

    // =========================================================================
    // AC-8: Key > 64 chars → 422 VALIDATION_FAILED / INVALID_FORMAT
    // =========================================================================

    @Test
    void testAc8_keyLongerThan64CharsReturns422InvalidFormat() throws Exception {
        String longKey = "k".repeat(65);
        String body    = "{\"memberId\":\"" + MEMBER_A + "\",\"sessionId\":\"" + SESSION_1 + "\"}";

        MvcResult result = mockMvc.perform(post("/test/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", longKey)
                        .header("X-Member-Id", MEMBER_A)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        ErrorEnvelope env = objectMapper.readValue(
                result.getResponse().getContentAsString(), ErrorEnvelope.class);
        assertThat(env.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(env.errors()).isNotNull().isNotEmpty();
        assertThat(env.errors().getFirst().code()).isEqualTo("INVALID_FORMAT");
        assertThat(env.errors().getFirst().field()).isEqualTo("Idempotency-Key");

        assertThat(stubController.creationCount.get())
                .as("No creation should occur when key is rejected")
                .isEqualTo(0);
    }

    @Test
    void testAc8_keyExactly64CharsIsAccepted() throws Exception {
        String exactKey = "k".repeat(64);
        String body     = "{\"memberId\":\"" + MEMBER_A + "\",\"sessionId\":\"" + SESSION_1 + "\"}";

        mockMvc.perform(post("/test/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", exactKey)
                        .header("X-Member-Id", MEMBER_A)
                        .content(body))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // AC-9: Replay of a cancelled booking returns cancelled body, not a new booking
    // =========================================================================

    @Test
    void testAc9_replayAfterCancellationReturnsCancelledBodyNotNewBooking() throws Exception {
        String body = "{\"memberId\":\"" + MEMBER_A + "\",\"sessionId\":\"" + SESSION_1 + "\"}";
        String key  = "key-cancel-replay";

        mockMvc.perform(post("/test/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .header("X-Member-Id", MEMBER_A)
                        .content(body))
                .andExpect(status().isCreated());

        // Simulate cancellation: service updates the body stored under the key
        stubController.cancelBooking(MEMBER_A, key, SESSION_1);

        MvcResult replay = mockMvc.perform(post("/test/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .header("X-Member-Id", MEMBER_A)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(replay.getResponse().getContentAsString()).contains("CANCELLED");
        assertThat(stubController.creationCount.get())
                .as("No new creation after cancellation")
                .isEqualTo(1);
    }

    // =========================================================================
    // AC-10: First create has Location header; replay has no Location header
    // =========================================================================

    @Test
    void testAc10_firstCreateHasLocationHeader() throws Exception {
        String body = "{\"memberId\":\"" + MEMBER_A + "\",\"sessionId\":\"" + SESSION_1 + "\"}";

        MvcResult result = mockMvc.perform(post("/test/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-loc-1")
                        .header("X-Member-Id", MEMBER_A)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(result.getResponse().getHeader("Location"))
                .as("First create must include a Location header")
                .isNotNull().isNotBlank();
    }

    @Test
    void testAc10_replayHasNoLocationHeader() throws Exception {
        String body = "{\"memberId\":\"" + MEMBER_A + "\",\"sessionId\":\"" + SESSION_1 + "\"}";

        mockMvc.perform(post("/test/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-loc-2")
                        .header("X-Member-Id", MEMBER_A)
                        .content(body))
                .andExpect(status().isCreated());

        MvcResult replay = mockMvc.perform(post("/test/bookings").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-loc-2")
                        .header("X-Member-Id", MEMBER_A)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(replay.getResponse().getHeader("Location"))
                .as("Replay must NOT include a Location header")
                .isNull();
    }
}
