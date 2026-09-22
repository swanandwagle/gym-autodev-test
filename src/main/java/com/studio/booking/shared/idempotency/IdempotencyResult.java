package com.studio.booking.shared.idempotency;

/**
 * Outcome of {@link IdempotencyService#checkReplay}.
 *
 * <ul>
 *   <li>{@link #isReplay()} true  → caller must return stored body with HTTP 200 and no Location header</li>
 *   <li>{@link #isReplay()} false → caller must proceed with the normal creation path</li>
 * </ul>
 */
public final class IdempotencyResult {

    private final boolean replay;
    private final int statusCode;
    private final String responseBody;

    private IdempotencyResult(boolean replay, int statusCode, String responseBody) {
        this.replay = replay;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /** Replay: return the original response with HTTP 200 and no Location header. */
    public static IdempotencyResult replay(int originalStatusCode, String originalBody) {
        return new IdempotencyResult(true, originalStatusCode, originalBody);
    }

    /** No prior record found; caller should proceed with creation. */
    public static IdempotencyResult proceed() {
        return new IdempotencyResult(false, 0, null);
    }

    public boolean isReplay() { return replay; }

    /** Original HTTP status code (meaningful only when {@link #isReplay()} is true). */
    public int statusCode() { return statusCode; }

    /** Original serialised JSON body (meaningful only when {@link #isReplay()} is true). */
    public String responseBody() { return responseBody; }
}
