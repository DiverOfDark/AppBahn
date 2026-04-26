package eu.appbahn.platform.common.idempotency;

import java.time.Duration;

/** Constants shared by the idempotency filter, OpenAPI customizer, and cleanup job. */
public final class IdempotencyConstants {

    /** Client-supplied request header carrying the dedup key. */
    public static final String HEADER_NAME = "Idempotency-Key";

    /**
     * Response header set when a request would have been a candidate for replay (has a key, is
     * a mutating method) but couldn't be cached. Lets clients know the next retry won't dedup.
     */
    public static final String REPLAYED_HEADER = "Idempotency-Replayed";

    /** Window during which a stored record will replay. Older rows behave as missing. */
    public static final Duration TTL = Duration.ofHours(24);

    /** Cap on captured request bodies. Larger bodies skip the cache. */
    public static final int MAX_BODY_BYTES = 1024 * 1024;

    private IdempotencyConstants() {}
}
