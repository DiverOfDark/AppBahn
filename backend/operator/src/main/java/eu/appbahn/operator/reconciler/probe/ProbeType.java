package eu.appbahn.operator.reconciler.probe;

import java.util.Locale;

/**
 * The three probe slots Kubernetes / {@code HealthCheckConfig} carry per resource. Used as the
 * map key inside {@link ProbeStatusTracker} and as the parsed value from kubelet event message
 * prefixes ("Liveness probe failed: ..." → {@link #LIVENESS}).
 */
public enum ProbeType {
    LIVENESS,
    READINESS,
    STARTUP;

    /**
     * Parse a kubelet {@code Unhealthy} event message and extract which probe failed. Returns
     * {@code null} if the message doesn't start with a recognized probe-type prefix (kubelet
     * occasionally emits {@code Unhealthy} for sidecar-injected probes etc.; we ignore those).
     *
     * <p>Kubelet message format is always {@code "<Liveness|Readiness|Startup> probe failed: ..."}
     * — see {@code pkg/kubelet/prober/prober.go} upstream.
     */
    public static ProbeType fromKubeletMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String trimmed = message.stripLeading();
        for (ProbeType type : values()) {
            String prefix = capitalize(type.name().toLowerCase(Locale.ROOT)) + " probe";
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return type;
            }
        }
        return null;
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
