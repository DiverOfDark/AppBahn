package eu.appbahn.operator.tunnel.query;

import io.fabric8.kubernetes.api.model.Quantity;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts a Kubernetes {@link Quantity} expressing CPU into integer millicores. Fabric8
 * already parses the unit suffix; we only need to normalise the resulting BigDecimal (in
 * cores) to milli units.
 */
final class QuantityToMillicores {

    private QuantityToMillicores() {}

    static long toMillicores(Quantity q) {
        BigDecimal cores = Quantity.getAmountInBytes(q);
        return cores.multiply(BigDecimal.valueOf(1000L))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
