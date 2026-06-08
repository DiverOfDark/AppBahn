package eu.appbahn.platform.resource.service;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * The two SSE frame types carried by {@code GET /resources/{slug}/logs/stream}. The wire name is
 * also the SSE {@code event:} name a client subscribes to.
 */
public enum LogStreamFrameType {
    LOG("log"),
    K8S_EVENT("k8s_event");

    private final String wireName;

    LogStreamFrameType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    /**
     * Parse the {@code types} query param (comma-separated wire names). A null/blank value selects
     * both types; unknown tokens are ignored. An explicit value that matches no known type also
     * falls back to both, so a typo never yields a silent dead stream.
     */
    public static Set<LogStreamFrameType> parse(String types) {
        if (types == null || types.isBlank()) {
            return EnumSet.allOf(LogStreamFrameType.class);
        }
        Set<LogStreamFrameType> out = EnumSet.noneOf(LogStreamFrameType.class);
        for (String token : types.split(",")) {
            String name = token.trim();
            Arrays.stream(values())
                    .filter(t -> t.wireName.equalsIgnoreCase(name))
                    .forEach(out::add);
        }
        return out.isEmpty() ? EnumSet.allOf(LogStreamFrameType.class) : out;
    }
}
