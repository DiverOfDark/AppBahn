package eu.appbahn.platform.tunnel.push;

import com.google.protobuf.Message;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Derives a stable, content-addressed revision from a protobuf snapshot. Same content on
 * any platform replica → same revision, so the subscribing operator can dedupe repeated
 * pushes without any cross-replica coordination. Caller is responsible for producing a
 * message with deterministic field ordering (e.g. sorting repeated entries) — the proto
 * wire format preserves insertion order.
 */
final class SnapshotRevisions {

    private SnapshotRevisions() {}

    static long contentRevision(Message snapshot) {
        byte[] bytes = snapshot.toByteArray();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            // Mask the sign bit so the resulting long is always positive — proto's
            // uint64 field would otherwise carry an awkwardly-negative signed long in Java.
            return ByteBuffer.wrap(digest, 0, 8).getLong() & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
