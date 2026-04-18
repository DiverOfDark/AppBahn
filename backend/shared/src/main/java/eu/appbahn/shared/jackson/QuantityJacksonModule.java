package eu.appbahn.shared.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.fabric8.kubernetes.api.model.Quantity;
import java.io.IOException;

/**
 * Serialises fabric8's {@link Quantity} as its compact string form (e.g. {@code "100m"}, {@code
 * "256Mi"}) rather than the default {@code {"amount":"100","format":"m"}} object. The public API
 * contract declares {@code cpu} / {@code memory} as strings; without this module every
 * {@code Resource} response would violate the spec and clients that validate response shape
 * (like the generated e2e OpenAPI client) would reject them.
 *
 * <p>Deserialisation is already handled by Quantity's own {@code @JsonCreator(DELEGATING) public
 * Quantity(String)} constructor, which accepts the string form — only serialisation needs a
 * custom hook.
 */
public class QuantityJacksonModule extends SimpleModule {

    public QuantityJacksonModule() {
        super("AppBahnQuantityModule");
        addSerializer(Quantity.class, new QuantityToStringSerializer());
    }

    private static final class QuantityToStringSerializer extends StdSerializer<Quantity> {
        QuantityToStringSerializer() {
            super(Quantity.class);
        }

        @Override
        public void serialize(Quantity value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.toString());
        }
    }
}
