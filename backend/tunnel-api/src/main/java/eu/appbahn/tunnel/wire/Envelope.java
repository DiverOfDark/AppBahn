package eu.appbahn.tunnel.wire;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Connect streaming-envelope codec.
 * <p>Frame layout: {@code [flags:1][length:4 BE][payload:length]}. Big-endian length is
 * unsigned in the spec; we reject anything that would overflow int (2 GiB) — our messages
 * are kilobytes, so an oversized length is always corruption or an attack.
 */
public final class Envelope {

    public static final int MAX_PAYLOAD = 16 * 1024 * 1024;

    private Envelope() {}

    public static void write(OutputStream out, byte flags, byte[] payload) throws IOException {
        if (payload.length > MAX_PAYLOAD) {
            throw new IOException("payload exceeds MAX_PAYLOAD: " + payload.length);
        }
        byte[] header = new byte[5];
        header[0] = flags;
        header[1] = (byte) ((payload.length >>> 24) & 0xFF);
        header[2] = (byte) ((payload.length >>> 16) & 0xFF);
        header[3] = (byte) ((payload.length >>> 8) & 0xFF);
        header[4] = (byte) (payload.length & 0xFF);
        out.write(header);
        out.write(payload);
    }

    /**
     * Read one envelope frame. Returns {@code null} on clean EOF (no header byte present).
     * Throws {@link IOException} if an EOF falls mid-frame, if the length is negative
     * (top bit set → 2+ GiB), if the payload exceeds {@link #MAX_PAYLOAD}, or if the
     * compression flag is set (we don't speak compression).
     */
    public static Frame read(InputStream in) throws IOException {
        int firstByte = in.read();
        if (firstByte < 0) {
            return null;
        }
        byte flags = (byte) firstByte;
        if ((flags & EnvelopeFlags.COMPRESSED) != 0) {
            throw new IOException("envelope compression flag set; not supported");
        }
        byte[] lenBytes = readExactly(in, 4);
        int length = ((lenBytes[0] & 0xFF) << 24)
                | ((lenBytes[1] & 0xFF) << 16)
                | ((lenBytes[2] & 0xFF) << 8)
                | (lenBytes[3] & 0xFF);
        if (length < 0 || length > MAX_PAYLOAD) {
            throw new IOException("envelope length out of range: " + length);
        }
        byte[] payload = length == 0 ? new byte[0] : readExactly(in, length);
        return new Frame(flags, payload);
    }

    private static byte[] readExactly(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int read = 0;
        while (read < n) {
            int r = in.read(buf, read, n - read);
            if (r < 0) {
                throw new EOFException("unexpected EOF mid-frame (wanted " + n + ", got " + read + ")");
            }
            read += r;
        }
        return buf;
    }

    public record Frame(byte flags, byte[] payload) {
        public boolean isEndStream() {
            return (flags & EnvelopeFlags.END_STREAM) != 0;
        }
    }
}
