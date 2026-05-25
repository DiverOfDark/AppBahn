package eu.appbahn.platform.api.tunnel;

import lombok.Data;

/**
 * Tells the operator to cancel an in-flight build (lifecycle {@code QUEUED} or {@code BUILDING})
 * for the named ImageSource. The operator deletes the in-flight build {@code Job}, marks the
 * matching {@code pendingBuild}/{@code queuedBuild} slot {@code CANCELED}, and emits the
 * corresponding {@code BuildLifecycleEvent}. Late-phase cancellation ({@code BUILT},
 * {@code ACTIVATING}) is rejected at the API boundary, not here — by the time this command
 * fires the platform has already verified the deployment is still in a cancellable phase.
 */
@Data
public class CancelBuild {

    /** SSE {@code event:} name that carries this payload. */
    public static final String EVENT_NAME = "cancel-build";

    private String correlationId;
    private String namespace;

    /** Name of the {@code ImageSource} CR whose build is being cancelled. */
    private String imageSourceName;

    /** Platform-side deployment row id correlating the cancel with the audit trail. */
    private String deploymentId;
}
