package eu.appbahn.platform.api.tunnel;

import java.time.OffsetDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.lang.Nullable;

/**
 * Operator→platform event: a core/v1 Event observed against an object owned by a Resource
 * (its pods, ReplicaSets, etc.). The operator's {@code ResourceEventWatcher} filters cluster-wide
 * Events down to the surfaced reasons and resolves the owning Resource by the
 * {@code appbahn.eu/resource} label, then forwards each match. The platform buffers recent events
 * per Resource so an open {@code /logs/stream} SSE can replay them as {@code k8s_event} frames.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceK8sEvent extends OperatorEvent {

    private String namespace;

    /** Resource slug; resolved from the involved object's {@code appbahn.eu/resource} label. */
    private String resourceSlug;

    /** Event {@code type}: {@code Normal} or {@code Warning}. */
    private String eventType;

    /** Event {@code reason} (Scheduled, Pulling, BackOff, OOMKilled, …). */
    private String reason;

    @Nullable
    private String message;

    /** Kind of the involved object (Pod, ReplicaSet, …). */
    @Nullable
    private String involvedKind;

    @Nullable
    private String involvedName;

    @Nullable
    private String pod;

    /** Number of times this event has fired (kubelet aggregates repeats). */
    private int count;

    @Nullable
    private OffsetDateTime eventTime;

    public ResourceK8sEvent() {
        setType("resource-k8s-event");
    }
}
