package eu.appbahn.shared.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public final class DeepClone {

    private DeepClone() {}

    public static <T> T of(T source, ObjectMapper mapper) {
        if (source == null) {
            return null;
        }
        try {
            byte[] bytes = mapper.writeValueAsBytes(source);
            @SuppressWarnings("unchecked")
            Class<T> type = (Class<T>) source.getClass();
            return mapper.readValue(bytes, type);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Deep clone failed for " + source.getClass().getName(), e);
        }
    }
}
