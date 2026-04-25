package eu.appbahn.shared.util;

import java.util.regex.Pattern;

/**
 * Validation pattern for inbound slugs at API boundaries. Accepts DNS-1123-lower:
 * starts with a letter, ends with an alphanumeric, internal characters may be
 * alphanumerics or hyphens, total length 2–30. Rejects path traversal, special
 * characters, oversized strings, and empty input.
 *
 * <p>{@link SlugGenerator}'s output ({@code <name 1..10>-<7 alphanums>}) is a strict
 * subset of what this accepts — this pattern adds slack for hand-crafted test data
 * and any future loosening of the canonical format.
 */
public final class SlugFormat {

    /** Compile-time constant for use in {@code @Pattern(regexp = …)}. */
    public static final String SLUG_REGEX = "^[a-z][a-z0-9-]{0,28}[a-z0-9]$";

    public static final Pattern SLUG_PATTERN = Pattern.compile(SLUG_REGEX);

    private SlugFormat() {}

    public static boolean isValid(String slug) {
        return slug != null && SLUG_PATTERN.matcher(slug).matches();
    }
}
