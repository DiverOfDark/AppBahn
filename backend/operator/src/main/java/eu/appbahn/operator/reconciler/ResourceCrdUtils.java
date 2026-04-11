package eu.appbahn.operator.reconciler;

import eu.appbahn.shared.crd.ResourceCrd;

/** Shared utility methods for inspecting {@link ResourceCrd} state. */
final class ResourceCrdUtils {

    private ResourceCrdUtils() {}

    static boolean hasEnvVars(ResourceCrd primary) {
        var config = primary.getSpec().getConfig();
        if (config != null && config.getEnv() != null && !config.getEnv().isEmpty()) {
            return true;
        }
        var links = primary.getSpec().getLinks();
        if (links != null) {
            for (var link : links) {
                if (link.getEnv() != null && !link.getEnv().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
}
