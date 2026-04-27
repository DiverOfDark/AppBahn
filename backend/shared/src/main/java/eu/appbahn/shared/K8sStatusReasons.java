package eu.appbahn.shared;

/**
 * Container/pod status reason strings as emitted by the kubelet. These are part
 * of the Kubernetes API contract — kept in one place so we never scatter the
 * literals across the operator.
 */
public final class K8sStatusReasons {

    private K8sStatusReasons() {}

    public static final String CRASH_LOOP_BACK_OFF = "CrashLoopBackOff";
    public static final String IMAGE_PULL_BACK_OFF = "ImagePullBackOff";
    public static final String ERR_IMAGE_PULL = "ErrImagePull";
    public static final String INVALID_IMAGE_NAME = "InvalidImageName";
    public static final String CREATE_CONTAINER_CONFIG_ERROR = "CreateContainerConfigError";
    public static final String CREATE_CONTAINER_ERROR = "CreateContainerError";
    public static final String RUN_CONTAINER_ERROR = "RunContainerError";

    /** Deployment.status.conditions[].reason when ProgressDeadlineSeconds elapses without progress. */
    public static final String PROGRESS_DEADLINE_EXCEEDED = "ProgressDeadlineExceeded";
}
