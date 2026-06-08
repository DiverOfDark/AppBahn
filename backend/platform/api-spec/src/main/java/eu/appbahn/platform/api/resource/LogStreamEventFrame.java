package eu.appbahn.platform.api.resource;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Data payload of a {@code k8s_event} frame on the {@code GET /resources/{slug}/logs/stream} SSE
 * stream: a core/v1 Event observed against an object owned by the Resource (its pods, ReplicaSets).
 * The operator filters cluster-wide Events to the surfaced reasons (Scheduled, Pulling/Pulled,
 * Created/Started, Killing, BackOff, Failed, OOMKilled, Evicted, Unhealthy, FailedScheduling,
 * ScalingReplicaSet, SuccessfulRescale).
 */
@Data
public class LogStreamEventFrame {

    /** Event {@code type}: {@code Normal} or {@code Warning}. */
    @Nullable
    private String eventType;

    /** Event {@code reason} (Scheduled, Pulling, BackOff, OOMKilled, …). */
    @Nullable
    private String reason;

    @Nullable
    private String message;

    @Nullable
    private String involvedKind;

    @Nullable
    private String involvedName;

    @Nullable
    private String pod;

    private int count;

    @Nullable
    private OffsetDateTime eventTime;
}
