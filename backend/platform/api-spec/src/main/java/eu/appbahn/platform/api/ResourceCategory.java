package eu.appbahn.platform.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;

/** Display category for a resource type. Serialized as lowercase on the wire. */
public enum ResourceCategory {
    @JsonProperty("deployment")
    DEPLOYMENT,
    @JsonProperty("database")
    DATABASE,
    @JsonProperty("storage")
    STORAGE,
    @JsonProperty("messaging")
    MESSAGING;

    /** Case-insensitive lookup; returns {@code null} for unknown values so mappers can degrade gracefully. */
    @JsonCreator
    public static ResourceCategory fromValue(String value) {
        if (value == null) return null;
        try {
            return ResourceCategory.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
