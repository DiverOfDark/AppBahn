package eu.appbahn.tunnel.wire;

/**
 * Flag bits for the first byte of a Connect stream envelope.
 * <p>
 * Wire format (per Connect streaming protocol): each frame on a server-stream body
 * starts with a 5-byte prefix: {@code [flags:1][length:4 big-endian]}, followed by
 * {@code length} bytes of payload.
 */
public final class EnvelopeFlags {

    /** Payload is compressed (gRPC-web convention). We do not use compression. */
    public static final byte COMPRESSED = 0x01;

    /** Payload is an end-of-stream trailers frame (Connect streaming). */
    public static final byte END_STREAM = 0x02;

    private EnvelopeFlags() {}
}
