package eu.appbahn.shared.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Small reflection helper for enum constants whose wire form is pinned via {@link JsonProperty}. */
public final class JacksonEnums {

    private JacksonEnums() {}

    /**
     * Returns the {@link JsonProperty#value()} on the constant — the canonical wire value.
     * Used to bridge our hand-written enums (e.g. {@code BuildLifecycle}) to openapi-generator
     * enums whose {@code fromValue(String)} expects the same wire shape.
     */
    public static String wireValue(Enum<?> constant) {
        try {
            JsonProperty annotation =
                    constant.getClass().getField(constant.name()).getAnnotation(JsonProperty.class);
            if (annotation == null) {
                throw new IllegalStateException("Enum constant "
                        + constant.getClass().getName() + "." + constant.name() + " is missing @JsonProperty");
            }
            return annotation.value();
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }
}
