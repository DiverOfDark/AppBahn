package eu.appbahn.platform.tunnel.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/**
 * Content-addressed revision of a snapshot DTO: identical content on any platform replica
 * yields the same revision, so the subscribing operator dedupes repeated pushes without
 * cross-replica coordination. Canonicalises by serialising with sorted map keys — the caller
 * is still responsible for deterministic ordering of repeated fields.
 */
@Component
class SnapshotRevisions {

    private final ObjectMapper canonical;

    SnapshotRevisions(ObjectMapper mapper) {
        this.canonical = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    long contentRevision(Object snapshot) {
        byte[] bytes;
        try {
            bytes = canonical.writeValueAsBytes(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not canonicalise snapshot for revision hash", e);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            // Mask the sign bit so the resulting long is always positive — keeps JSON numbers clean.
            return ByteBuffer.wrap(digest, 0, 8).getLong() & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
