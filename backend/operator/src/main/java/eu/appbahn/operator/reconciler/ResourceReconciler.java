package eu.appbahn.operator.reconciler;

import eu.appbahn.operator.reconciler.imagesource.ResourceReleaseResolver;
import eu.appbahn.operator.tunnel.OperatorEventPublisher;
import eu.appbahn.shared.K8sStatusReasons;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ActiveRelease;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceStatusDetail;
import eu.appbahn.shared.crd.RolloutStatus;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ControllerConfiguration
@Workflow(
        dependents = {
            @Dependent(
                    name = "deployment",
                    type = DeploymentDependentResource.class,
                    reconcilePrecondition = DeploymentReconcileCondition.class),
            @Dependent(
                    name = "configmap",
                    type = ConfigMapDependentResource.class,
                    reconcilePrecondition = EnvDefinedCondition.class),
            @Dependent(
                    name = "ingress-service",
                    type = IngressServiceDependentResource.class,
                    reconcilePrecondition = PortDefinedCondition.class),
            @Dependent(
                    name = "tcp-service",
                    type = TcpServiceDependentResource.class,
                    reconcilePrecondition = ExposeTcpCondition.class),
            @Dependent(
                    name = "ingress",
                    type = IngressDependentResource.class,
                    reconcilePrecondition = ExposeIngressCondition.class),
            @Dependent(
                    name = "pdb",
                    type = PdbDependentResource.class,
                    reconcilePrecondition = PdbReconcileCondition.class),
        })
public class ResourceReconciler implements Reconciler<ResourceCrd>, Cleaner<ResourceCrd> {

    private static final Logger log = LoggerFactory.getLogger(ResourceReconciler.class);

    /** Waiting reasons that will never start successfully — surface them without waiting for progressDeadlineSeconds. */
    private static final Set<String> TERMINAL_WAITING_REASONS = Set.of(
            K8sStatusReasons.IMAGE_PULL_BACK_OFF,
            K8sStatusReasons.ERR_IMAGE_PULL,
            K8sStatusReasons.INVALID_IMAGE_NAME,
            K8sStatusReasons.CREATE_CONTAINER_CONFIG_ERROR,
            K8sStatusReasons.CREATE_CONTAINER_ERROR,
            K8sStatusReasons.RUN_CONTAINER_ERROR);

    /** Retry interval after an unexpected reconcile error — informers don't replay platform-sync failures. */
    private static final long ERROR_RETRY_MINUTES = 1;

    private final OperatorEventPublisher eventPublisher;
    private final ReleaseLifecycleEmitter releaseLifecycleEmitter;
    private final Counter syncFailureCounter;

    public ResourceReconciler(
            OperatorEventPublisher eventPublisher,
            ReleaseLifecycleEmitter releaseLifecycleEmitter,
            MeterRegistry meterRegistry) {
        this.eventPublisher = eventPublisher;
        this.releaseLifecycleEmitter = releaseLifecycleEmitter;
        this.syncFailureCounter = Counter.builder("appbahn.operator.sync.failures")
                .description("Number of failed platform sync attempts")
                .register(meterRegistry);
    }

    /**
     * Extra event source: watch Pods carrying {@link Labels#RESOURCE_KEY} so pod-level failures
     * (ImagePullBackOff, CrashLoopBackOff, ...) trigger a reconcile without waiting 120s for
     * the Deployment's ProgressDeadlineExceeded. JOSDK already wires the Deployment/Ingress.
     */
    @Override
    public List<EventSource<?, ResourceCrd>> prepareEventSources(EventSourceContext<ResourceCrd> context) {
        var podInformerConfig = InformerEventSourceConfiguration.from(Pod.class, ResourceCrd.class)
                .withLabelSelector(Labels.RESOURCE_KEY)
                .withSecondaryToPrimaryMapper(pod -> {
                    if (pod.getMetadata() == null || pod.getMetadata().getLabels() == null) {
                        return Set.of();
                    }
                    String resourceName = pod.getMetadata().getLabels().get(Labels.RESOURCE_KEY);
                    if (resourceName == null) {
                        return Set.of();
                    }
                    return Set.of(new ResourceID(resourceName, pod.getMetadata().getNamespace()));
                })
                .build();
        // Watch ImageSource → Resource so a fresh latestArtifact triggers re-render of the
        // bound Resource's pod template without waiting for the next reconcile tick. Binding
        // is by same name in the same namespace.
        var imageSourceInformerConfig = InformerEventSourceConfiguration.from(ImageSourceCrd.class, ResourceCrd.class)
                .withSecondaryToPrimaryMapper(imageSource -> {
                    if (imageSource.getMetadata() == null) {
                        return Set.of();
                    }
                    String namespace = imageSource.getMetadata().getNamespace();
                    String imageSourceName = imageSource.getMetadata().getName();
                    if (namespace == null || imageSourceName == null) {
                        return Set.of();
                    }
                    return Set.of(new ResourceID(imageSourceName, namespace));
                })
                .build();
        return List.of(
                new InformerEventSource<>(podInformerConfig, context),
                new InformerEventSource<>(imageSourceInformerConfig, context));
    }

    @Override
    public UpdateControl<ResourceCrd> reconcile(ResourceCrd resource, Context<ResourceCrd> context) {
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        log.info("Reconciling Resource: {} in namespace {}", name, namespace);

        try {
            // Null spec can leak into the watch cache during delete/create races — skip
            // reconciling those rather than NPE'ing into an uncaught-error retry loop.
            if (resource.getSpec() == null) {
                log.debug("Reconcile called with null spec for {}; skipping", name);
                return UpdateControl.noUpdate();
            }

            var k8sDeployment = context.getSecondaryResource(Deployment.class).orElse(null);
            ResourceStatusDetail previous = resource.getStatus();
            var status = deriveStatus(resource, k8sDeployment, context);
            enrichWithReleaseStatus(resource, status, context);
            releaseLifecycleEmitter.reconcile(resource, previous, status);

            if (statusEquals(previous, status)) {
                log.debug("Status unchanged for {}, skipping update", name);
                return UpdateControl.noUpdate();
            }

            resource.setStatus(status);
            syncToPlatform(resource);

            return UpdateControl.patchStatus(resource);
        } catch (Exception e) {
            log.error("Error reconciling resource {}: {}", name, e.getMessage(), e);
            resource.setStatus(createErrorStatus(resource, e.getMessage(), null));
            syncToPlatform(resource);
            return UpdateControl.<ResourceCrd>patchStatus(resource)
                    .rescheduleAfter(ERROR_RETRY_MINUTES, TimeUnit.MINUTES);
        }
    }

    @Override
    public DeleteControl cleanup(ResourceCrd resource, Context<ResourceCrd> context) {
        String name = resource.getMetadata().getName();
        log.info("Cleaning up resource: {}", name);

        try {
            eventPublisher.emitDeleted(name);
            log.info("Notified platform of resource deletion: {}", name);
        } catch (Exception e) {
            log.warn("Failed to notify platform of deletion for {}: {}", name, e.getMessage());
        }

        return DeleteControl.defaultDelete();
    }

    private ResourceStatusDetail createErrorStatus(ResourceCrd resource, String message, String reason) {
        var status = new ResourceStatusDetail();
        status.setPhase(ResourcePhase.ERROR);
        status.setMessage(message);
        status.setObservedGeneration(resource.getMetadata().getGeneration());
        if (reason != null) {
            var condition = new ResourceStatusDetail.ResourceCondition();
            condition.setType("Ready");
            condition.setStatus("False");
            condition.setReason(reason);
            condition.setMessage(message);
            condition.setLastUpdateTime(java.time.Instant.now());
            status.setConditions(List.of(condition));
        }
        return status;
    }

    private ResourceStatusDetail deriveStatus(
            ResourceCrd resource, Deployment k8sDeployment, Context<ResourceCrd> context) {
        var status = new ResourceStatusDetail();
        status.setObservedGeneration(resource.getMetadata().getGeneration());
        if (resource.getStatus() != null) {
            status.setSyncFailed(resource.getStatus().getSyncFailed());
        }

        boolean stopped = Boolean.TRUE.equals(resource.getSpec().getStopped());

        if (k8sDeployment == null) {
            if (stopped) {
                status.setPhase(ResourcePhase.STOPPED);
                status.setMessage("Resource is stopped");
                return status;
            }
            status.setPhase(ResourcePhase.PENDING);
            status.setMessage("Waiting for deployment");
            return status;
        }
        if (k8sDeployment.getStatus() == null) {
            status.setPhase(ResourcePhase.PENDING);
            status.setMessage("Waiting for deployment");
            return status;
        }

        var depStatus = k8sDeployment.getStatus();
        int desired = k8sDeployment.getSpec().getReplicas() != null
                ? k8sDeployment.getSpec().getReplicas()
                : 1;
        int ready = depStatus.getReadyReplicas() != null ? depStatus.getReadyReplicas() : 0;
        int updated = depStatus.getUpdatedReplicas() != null ? depStatus.getUpdatedReplicas() : 0;
        int available = depStatus.getAvailableReplicas() != null ? depStatus.getAvailableReplicas() : 0;

        if (stopped) {
            status.setPhase(ResourcePhase.PENDING);
            status.setMessage("Stopping...");
            var replicas = new ResourceStatusDetail.ReplicaStatus();
            replicas.setDesired(desired);
            replicas.setReady(ready);
            replicas.setUpdated(updated);
            replicas.setAvailable(available);
            status.setReplicas(replicas);
            return status;
        }

        if (desired == 0 && DeploymentDependentResource.isCurrentRevisionFailed(resource)) {
            var prev = resource.getStatus();
            status.setPhase(ResourcePhase.ERROR);
            status.setMessage(
                    prev != null && prev.getMessage() != null
                            ? prev.getMessage()
                            : "Deployment terminally failed; scaled to 0 to stop restart loop");
            if (prev != null && prev.getLastError() != null) {
                status.setLastError(prev.getLastError());
            }
            var replicas = new ResourceStatusDetail.ReplicaStatus();
            replicas.setDesired(desired);
            replicas.setReady(ready);
            replicas.setUpdated(updated);
            replicas.setAvailable(available);
            status.setReplicas(replicas);
            return status;
        }

        var replicas = new ResourceStatusDetail.ReplicaStatus();
        replicas.setDesired(desired);
        replicas.setReady(ready);
        replicas.setUpdated(updated);
        replicas.setAvailable(available);
        status.setReplicas(replicas);

        var config = resource.getSpec().getConfig();
        var ingressPorts =
                config != null ? config.getIngressPorts() : List.<eu.appbahn.shared.crd.ResourceConfig.PortConfig>of();
        if (!ingressPorts.isEmpty()) {
            var liveHosts = liveIngressHosts(context.getSecondaryResources(Ingress.class));
            var domains = new java.util.ArrayList<ResourceStatusDetail.CustomDomainStatus>();
            for (var port : ingressPorts) {
                String domain = port.getDomain();
                if (domain != null && liveHosts.contains(domain)) {
                    var cd = new ResourceStatusDetail.CustomDomainStatus();
                    cd.setDomain(domain);
                    if (port.getPort() != null) {
                        cd.setPort(port.getPort());
                    }
                    cd.setStatus(eu.appbahn.shared.crd.DomainStatus.ACTIVE);
                    domains.add(cd);
                }
            }
            if (!domains.isEmpty()) {
                status.setCustomDomains(domains);
            }
        }

        PodInspection inspection = inspectPods(context.getSecondaryResources(Pod.class));
        if (inspection.lastError != null) {
            status.setLastError(inspection.lastError);
        }

        Optional<String> terminalFailure = inspection.terminalFailure;
        boolean conditionRolloutFailed = hasFailedRolloutCondition(depStatus);
        boolean rolloutFailed = conditionRolloutFailed || terminalFailure.isPresent();

        if (rolloutFailed && ready > 0) {
            status.setPhase(ResourcePhase.DEGRADED);
            status.setMessage(terminalFailure
                    .map(reason -> reason + " (old replicas still serving)")
                    .orElseGet(() -> "Deployment rollout failed but " + ready + " old replica(s) still running"));
        } else if (rolloutFailed) {
            status.setPhase(ResourcePhase.ERROR);
            status.setMessage(terminalFailure.orElse("Deployment rollout failed"));
        } else if (inspection.crashLoopReason != null) {
            status.setPhase(ResourcePhase.ERROR);
            status.setMessage(inspection.crashLoopReason);
        } else if (ready >= desired && desired > 0) {
            status.setPhase(ResourcePhase.READY);
            status.setMessage(null);
        } else if (ready > 0) {
            if (updated < desired) {
                status.setPhase(ResourcePhase.RESTARTING);
                status.setMessage("Rolling update in progress: " + ready + "/" + desired + " replicas ready");
            } else {
                status.setPhase(ResourcePhase.DEGRADED);
                status.setMessage(ready + "/" + desired + " replicas ready");
            }
        } else {
            status.setPhase(ResourcePhase.PENDING);
            status.setMessage("Waiting for pods to be ready");
        }

        return status;
    }

    /**
     * Populate the release-path fields ({@code activeRelease}, {@code observedReleaseId},
     * {@code rolloutStatus}, {@code replicasReady}) from the resolved active artifact (pin first,
     * then ImageSource {@code latestArtifact}) and the K8s deployment status.
     */
    static void enrichWithReleaseStatus(
            ResourceCrd resource, ResourceStatusDetail status, Context<ResourceCrd> context) {
        ResourceReleaseResolver.resolveActiveArtifact(resource, context).ifPresent(artifact -> {
            ActiveRelease activeRelease = new ActiveRelease();
            activeRelease.setSourceCommit(artifact.getSourceCommit());
            activeRelease.setImageRef(artifact.getImageRef());
            activeRelease.setActivatedAt(artifact.getBuiltAt());
            status.setActiveRelease(activeRelease);
        });
        if (status.getReplicas() != null) {
            status.setReplicasReady(status.getReplicas().getReady());
        }
        status.setRolloutStatus(deriveRolloutStatus(status));
    }

    /**
     * Map the {@link ResourcePhase} (computed against the same K8s deployment facts) onto the
     * {@link RolloutStatus} surface.
     */
    static RolloutStatus deriveRolloutStatus(ResourceStatusDetail status) {
        if (status.getActiveRelease() == null) {
            return RolloutStatus.Pending;
        }
        ResourcePhase phase = status.getPhase();
        if (phase == null) {
            return RolloutStatus.Pending;
        }
        return switch (phase) {
            case READY -> RolloutStatus.Healthy;
            case DEGRADED -> RolloutStatus.Degraded;
            case ERROR -> RolloutStatus.Failed;
            case RESTARTING, PENDING -> RolloutStatus.Deploying;
            case STOPPED -> RolloutStatus.Pending;
        };
    }

    /**
     * Walk every container in every non-terminating pod and collect:
     * <ul>
     *   <li>{@link PodInspection#terminalFailure} — first pod hitting a {@link #TERMINAL_WAITING_REASONS}
     *       reason (ImagePullBackOff &amp; friends — never recovers without a spec edit).</li>
     *   <li>{@link PodInspection#crashLoopReason} — first pod with a container in
     *       {@code CrashLoopBackOff}, formatted as e.g. "container 'app' crash-looped: exited 137 (OOMKilled)".</li>
     *   <li>{@link PodInspection#lastError} — most informative human-readable explanation found,
     *       drawn from waiting reasons or {@code lastState.terminated.exitCode/reason}.</li>
     * </ul>
     */
    static PodInspection inspectPods(Set<Pod> pods) {
        Optional<String> terminalFailure = Optional.empty();
        String crashLoopReason = null;
        String lastError = null;
        for (Pod pod : pods) {
            if (pod.getMetadata() != null && pod.getMetadata().getDeletionTimestamp() != null) {
                continue;
            }
            if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
                continue;
            }
            for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                String waitingReason = cs.getState() != null && cs.getState().getWaiting() != null
                        ? cs.getState().getWaiting().getReason()
                        : null;
                String waitingMessage = cs.getState() != null && cs.getState().getWaiting() != null
                        ? cs.getState().getWaiting().getMessage()
                        : null;
                String previousTermination = describePreviousTermination(cs);

                if (waitingReason != null) {
                    if (terminalFailure.isEmpty() && TERMINAL_WAITING_REASONS.contains(waitingReason)) {
                        terminalFailure = Optional.of(formatFailure(waitingReason, waitingMessage));
                    }
                    if (crashLoopReason == null && K8sStatusReasons.CRASH_LOOP_BACK_OFF.equals(waitingReason)) {
                        crashLoopReason = describeCrashLoop(cs.getName(), previousTermination, waitingMessage);
                    }
                    if (lastError == null) {
                        lastError = describeContainerState(
                                cs.getName(), waitingReason, waitingMessage, previousTermination);
                    }
                } else if (lastError == null && previousTermination != null) {
                    lastError = "container '" + cs.getName() + "' previously " + previousTermination;
                }
            }
        }
        return new PodInspection(terminalFailure, crashLoopReason, lastError);
    }

    private static String describePreviousTermination(ContainerStatus cs) {
        if (cs.getLastState() == null || cs.getLastState().getTerminated() == null) {
            return null;
        }
        ContainerStateTerminated term = cs.getLastState().getTerminated();
        Integer code = term.getExitCode();
        String reason = term.getReason();
        if (code == null && (reason == null || reason.isBlank())) {
            return null;
        }
        StringBuilder sb = new StringBuilder("exited");
        if (code != null) {
            sb.append(' ').append(code);
        }
        if (reason != null && !reason.isBlank()) {
            sb.append(" (").append(reason).append(')');
        }
        return sb.toString();
    }

    private static String describeCrashLoop(String container, String previousTermination, String waitingMessage) {
        StringBuilder sb = new StringBuilder("container '")
                .append(container != null ? container : "?")
                .append("' crash-looped");
        if (previousTermination != null) {
            sb.append(": ").append(previousTermination);
        } else if (waitingMessage != null && !waitingMessage.isBlank()) {
            sb.append(": ").append(waitingMessage);
        }
        return sb.toString();
    }

    private static String describeContainerState(
            String container, String waitingReason, String waitingMessage, String previousTermination) {
        if (K8sStatusReasons.CRASH_LOOP_BACK_OFF.equals(waitingReason)) {
            return describeCrashLoop(container, previousTermination, waitingMessage);
        }
        StringBuilder sb = new StringBuilder("container '")
                .append(container != null ? container : "?")
                .append("' ")
                .append(waitingReason);
        if (waitingMessage != null && !waitingMessage.isBlank()) {
            sb.append(": ").append(waitingMessage);
        } else if (previousTermination != null) {
            sb.append(" (previously ").append(previousTermination).append(')');
        }
        return sb.toString();
    }

    private static String formatFailure(String reason, String message) {
        return message != null && !message.isBlank() ? reason + ": " + message : reason;
    }

    record PodInspection(Optional<String> terminalFailure, String crashLoopReason, String lastError) {}

    private static boolean hasFailedRolloutCondition(io.fabric8.kubernetes.api.model.apps.DeploymentStatus depStatus) {
        if (depStatus.getConditions() == null) {
            return false;
        }
        return depStatus.getConditions().stream()
                .anyMatch(c -> "Progressing".equals(c.getType())
                        && "False".equals(c.getStatus())
                        && K8sStatusReasons.PROGRESS_DEADLINE_EXCEEDED.equals(c.getReason()));
    }

    private static Set<String> liveIngressHosts(Set<Ingress> ingresses) {
        var hosts = new java.util.HashSet<String>();
        for (var ingress : ingresses) {
            if (ingress.getSpec() == null || ingress.getSpec().getRules() == null) {
                continue;
            }
            for (var rule : ingress.getSpec().getRules()) {
                if (rule.getHost() != null) {
                    hosts.add(rule.getHost());
                }
            }
        }
        return hosts;
    }

    private static boolean statusEquals(ResourceStatusDetail a, ResourceStatusDetail b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getPhase(), b.getPhase())
                && Objects.equals(a.getMessage(), b.getMessage())
                && Objects.equals(a.getLastError(), b.getLastError())
                && Objects.equals(a.getObservedGeneration(), b.getObservedGeneration())
                && Objects.equals(a.getCustomDomains(), b.getCustomDomains())
                && Objects.equals(a.getActiveRelease(), b.getActiveRelease())
                && Objects.equals(a.getObservedReleaseId(), b.getObservedReleaseId())
                && Objects.equals(a.getObservedRestartGeneration(), b.getObservedRestartGeneration())
                && Objects.equals(a.getObservedEnvHash(), b.getObservedEnvHash())
                && Objects.equals(a.getObservedPinnedImageRef(), b.getObservedPinnedImageRef())
                && Objects.equals(a.getRolloutStatus(), b.getRolloutStatus())
                && a.getReplicasReady() == b.getReplicasReady()
                && Objects.equals(a.getSyncFailed(), b.getSyncFailed())
                && replicasEqual(a.getReplicas(), b.getReplicas());
    }

    private static boolean replicasEqual(ResourceStatusDetail.ReplicaStatus a, ResourceStatusDetail.ReplicaStatus b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.getDesired() == b.getDesired()
                && a.getReady() == b.getReady()
                && a.getUpdated() == b.getUpdated()
                && a.getAvailable() == b.getAvailable();
    }

    private void syncToPlatform(ResourceCrd resource) {
        try {
            eventPublisher.emitSync(resource);
            log.debug("Enqueued sync for resource {}", resource.getMetadata().getName());
            if (resource.getStatus() != null) {
                resource.getStatus().setSyncFailed(false);
            }
        } catch (Exception e) {
            syncFailureCounter.increment();
            log.warn(
                    "Failed to enqueue sync for resource {}: {}",
                    resource.getMetadata().getName(),
                    e.getMessage());
            if (resource.getStatus() != null) {
                resource.getStatus().setSyncFailed(true);
            }
        }
    }
}
