package eu.appbahn.shared.util;

import java.security.SecureRandom;

public final class SlugGenerator {

    private static final int NAME_MAX_LENGTH = 10;
    private static final int SUFFIX_LENGTH = 7;
    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private SlugGenerator() {}

    public static String generate(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            normalized = "r";
        }
        if (normalized.length() > NAME_MAX_LENGTH) {
            normalized = normalized.substring(0, NAME_MAX_LENGTH);
        }
        return normalized + "-" + randomSuffix();
    }

    static String normalize(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static String randomSuffix() {
        var sb = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
