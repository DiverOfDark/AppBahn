package eu.appbahn.platform.resource.service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Mints opaque random capability tokens for the webhook receiver. 32 bytes of entropy emitted
 * as URL-safe base64 (no padding) — fits in a path segment and stays under the 64-char column.
 */
public final class WebhookTokenGenerator {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private WebhookTokenGenerator() {}

    public static String generate() {
        byte[] buf = new byte[TOKEN_BYTES];
        RNG.nextBytes(buf);
        return ENCODER.encodeToString(buf);
    }

    /**
     * Mask a token so the public form keeps the last 4 chars (recognisable in dashboards) but
     * never leaks the full secret on a re-read of {@code GET .../webhook}.
     */
    public static String mask(String token) {
        if (token == null || token.length() <= 4) {
            return "wh_••••";
        }
        return "wh_••••••••" + token.substring(token.length() - 4);
    }
}
