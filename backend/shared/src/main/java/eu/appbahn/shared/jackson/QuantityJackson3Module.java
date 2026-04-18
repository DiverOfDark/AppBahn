package eu.appbahn.shared.jackson;

import io.fabric8.kubernetes.api.model.Quantity;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Jackson 3 counterpart of {@link QuantityJacksonModule}. Spring Boot 4 uses Jackson 3 (the {@code
 * tools.jackson.*} packages) for its HTTP {@code HttpMessageConverter}, so the Jackson 2 module we
 * register on the legacy {@code com.fasterxml} ObjectMapper doesn't apply to REST responses. This
 * class registers the equivalent string-form serializer on the Jackson 3 side so platform API
 * responses serialize {@code cpu}/{@code memory} as {@code "100m"} / {@code "256Mi"} strings as
 * the public-api.yaml contract promises.
 */
public class QuantityJackson3Module extends SimpleModule {

    public QuantityJackson3Module() {
        super("AppBahnQuantityJackson3Module");
        addSerializer(Quantity.class, new QuantityToStringSerializer());
    }

    private static final class QuantityToStringSerializer extends StdSerializer<Quantity> {
        QuantityToStringSerializer() {
            super(Quantity.class);
        }

        @Override
        public void serialize(Quantity value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeString(value.toString());
        }
    }
}
