package eu.appbahn.operator.tunnel.query;

import io.fabric8.kubernetes.api.model.Quantity;
import java.math.RoundingMode;

/**
 * Converts a Kubernetes {@link Quantity} expressing memory (or any byte-suffixed value)
 * into integer bytes via fabric8's built-in parser.
 */
final class QuantityToBytes {

    private QuantityToBytes() {}

    static long toBytes(Quantity q) {
        return Quantity.getAmountInBytes(q).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
