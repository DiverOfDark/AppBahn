package eu.appbahn.platform.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Display category for a resource type. */
public enum ResourceCategory {
    @JsonProperty("Deployment")
    DEPLOYMENT,
    @JsonProperty("Database")
    DATABASE,
    @JsonProperty("Storage")
    STORAGE,
    @JsonProperty("Messaging")
    MESSAGING;

    /**
     * Strict PascalCase lookup; returns {@code null} for unknown values so callers can degrade
     * gracefully (resource-type definitions arrive from user-supplied JSONB and may carry typos).
     */
    @JsonCreator
    public static ResourceCategory fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ResourceCategory c : values()) {
            if (c.name().equalsIgnoreCase(value.replace("_", ""))
                    || toPascal(c.name()).equals(value)) {
                return c;
            }
        }
        return null;
    }

    private static String toPascal(String constantName) {
        StringBuilder out = new StringBuilder(constantName.length());
        for (String part : constantName.split("_")) {
            if (part.isEmpty()) continue;
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return out.toString();
    }
}
