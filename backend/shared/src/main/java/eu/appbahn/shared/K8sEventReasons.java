package eu.appbahn.shared;

import java.util.Set;

/**
 * {@code Event.reason} values surfaced on the Resource log/event stream. These are part of the
 * Kubernetes API contract (emitted by the scheduler, kubelet, and the deployment/replicaset
 * controllers) — kept in one place so the operator's event watcher never scatters the literals.
 *
 * <p>The event {@code type} field ({@code Normal} / {@code Warning}) lives in {@link #NORMAL} /
 * {@link #WARNING}.
 */
public final class K8sEventReasons {

    private K8sEventReasons() {}

    public static final String NORMAL = "Normal";
    public static final String WARNING = "Warning";

    // Scheduler
    public static final String SCHEDULED = "Scheduled";
    public static final String FAILED_SCHEDULING = "FailedScheduling";

    // Kubelet — image + container lifecycle
    public static final String PULLING = "Pulling";
    public static final String PULLED = "Pulled";
    public static final String CREATED = "Created";
    public static final String STARTED = "Started";
    public static final String KILLING = "Killing";
    public static final String BACK_OFF = "BackOff";
    public static final String FAILED = "Failed";
    public static final String OOM_KILLED = "OOMKilled";
    public static final String EVICTED = "Evicted";
    public static final String UNHEALTHY = "Unhealthy";

    // Deployment / ReplicaSet controllers
    public static final String SCALING_REPLICA_SET = "ScalingReplicaSet";
    public static final String SUCCESSFUL_RESCALE = "SuccessfulRescale";

    /** The full set surfaced on {@code /resources/{slug}/logs/stream}. */
    public static final Set<String> SURFACED = Set.of(
            SCHEDULED,
            FAILED_SCHEDULING,
            PULLING,
            PULLED,
            CREATED,
            STARTED,
            KILLING,
            BACK_OFF,
            FAILED,
            OOM_KILLED,
            EVICTED,
            UNHEALTHY,
            SCALING_REPLICA_SET,
            SUCCESSFUL_RESCALE);
}
