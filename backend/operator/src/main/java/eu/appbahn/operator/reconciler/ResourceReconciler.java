package eu.appbahn.operator.reconciler;

import eu.appbahn.operator.tunnel.OperatorEventPublisher;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceStatusDetail;
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
        })
public class ResourceReconciler implements Reconciler<ResourceCrd>, Cleaner<ResourceCrd> {

    private static final Logger log = LoggerFactory.getLogger(ResourceReconciler.class);

    /** Waiting reasons that will never start successfully — surface them without waiting for progressDeadlineSeconds. */
    private static final Set<String> TERMINAL_WAITING_REASONS = Set.of(
            "ImagePullBackOff",
            "ErrImagePull",
            "InvalidImageName",
            "CreateContainerConfigError",
            "CreateContainerError",
            "RunContainerError");

    /** CrashLoopBackOff is terminal only after this many restarts — gives transient-race startups time to recover. */
    private static final int CRASHLOOP_RESTART_THRESHOLD = 3;

    private final OperatorEventPublisher eventPublisher;
    private final long rescheduleSeconds;
    private final Counter syncFailureCounter;

    public ResourceReconciler(
            OperatorEventPublisher eventPublisher,
            MeterRegistry meterRegistry,
            @org.springframework.beans.factory.annotation.Value("${operator.reschedule-seconds:300}")
                    long rescheduleSeconds) {
        this.eventPublisher = eventPublisher;
        this.rescheduleSeconds = rescheduleSeconds;
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
        return List.of(new InformerEventSource<>(podInformerConfig, context));
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
                return UpdateControl.<ResourceCrd>noUpdate().rescheduleAfter(rescheduleSeconds, TimeUnit.SECONDS);
            }
            var config = resource.getSpec().getConfig();
            if (config == null
                    || !(config.getSource() instanceof eu.appbahn.shared.crd.DockerSource dockerSrc)
                    || dockerSrc.getImage() == null) {
                resource.setStatus(createErrorStatus(resource, "docker source with image is required"));
                syncToPlatform(resource);
                return UpdateControl.<ResourceCrd>patchStatus(resource)
                        .rescheduleAfter(rescheduleSeconds, TimeUnit.SECONDS);
            }

            var k8sDeployment = context.getSecondaryResource(Deployment.class).orElse(null);
            var status = deriveStatus(resource, k8sDeployment, context);

            if (statusEquals(resource.getStatus(), status)) {
                log.debug("Status unchanged for {}, skipping update", name);
                return UpdateControl.<ResourceCrd>noUpdate().rescheduleAfter(rescheduleSeconds, TimeUnit.SECONDS);
            }

            resource.setStatus(status);
            syncToPlatform(resource);

            return UpdateControl.<ResourceCrd>patchStatus(resource)
                    .rescheduleAfter(rescheduleSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Error reconciling resource {}: {}", name, e.getMessage(), e);
            resource.setStatus(createErrorStatus(resource, e.getMessage()));
            syncToPlatform(resource);
            return UpdateControl.<ResourceCrd>patchStatus(resource).rescheduleAfter(1, TimeUnit.MINUTES);
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

    private ResourceStatusDetail createErrorStatus(ResourceCrd resource, String message) {
        var status = new ResourceStatusDetail();
        status.setPhase(ResourcePhase.ERROR);
        status.setMessage(message);
        status.setObservedGeneration(resource.getMetadata().getGeneration());
        return status;
    }

    private ResourceStatusDetail deriveStatus(
            ResourceCrd resource, Deployment k8sDeployment, Context<ResourceCrd> context) {
        var status = new ResourceStatusDetail();
        status.setObservedGeneration(resource.getMetadata().getGeneration());
        // syncFailed is owned by syncToPlatform, not derived from K8s.
        if (resource.getStatus() != null) {
            status.setSyncFailed(resource.getStatus().getSyncFailed());
        }

        String deploymentRevision = resource.getSpec().getDeploymentRevision();
        if (deploymentRevision != null) {
            status.setLatestDeploymentId(deploymentRevision);
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
            if (deploymentRevision != null) {
                status.setLatestDeploymentStatus(eu.appbahn.shared.crd.DeploymentStatus.DEPLOYING);
            }
            return status;
        }
        if (k8sDeployment.getStatus() == null) {
            status.setPhase(ResourcePhase.PENDING);
            status.setMessage("Waiting for deployment");
            if (deploymentRevision != null) {
                status.setLatestDeploymentStatus(eu.appbahn.shared.crd.DeploymentStatus.DEPLOYING);
            }
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
            // Deployment is still present mid-teardown — JOSDK will delete it, but until then
            // surface a transitional PENDING/"Stopping..." rather than flipping straight to
            // STOPPED. The k8sDeployment==null branch above handles the terminal STOPPED state.
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

        // DeploymentDependentResource scales to 0 on a terminally-failed revision (see
        // isCurrentRevisionFailed). Preserve ERROR so status doesn't flip to PENDING once
        // the crashing pods are gone.
        if (desired == 0 && DeploymentDependentResource.isCurrentRevisionFailed(resource)) {
            var prev = resource.getStatus();
            status.setPhase(ResourcePhase.ERROR);
            status.setMessage(
                    prev != null && prev.getMessage() != null
                            ? prev.getMessage()
                            : "Deployment terminally failed; scaled to 0 to stop restart loop");
            var replicas = new ResourceStatusDetail.ReplicaStatus();
            replicas.setDesired(desired);
            replicas.setReady(ready);
            replicas.setUpdated(updated);
            replicas.setAvailable(available);
            status.setReplicas(replicas);
            if (deploymentRevision != null) {
                status.setLatestDeploymentStatus(eu.appbahn.shared.crd.DeploymentStatus.FAILED);
            }
            return status;
        }

        var replicas = new ResourceStatusDetail.ReplicaStatus();
        replicas.setDesired(desired);
        replicas.setReady(ready);
        replicas.setUpdated(updated);
        replicas.setAvailable(available);
        status.setReplicas(replicas);

        // Derive from Ingress secondary, not spec echo.
        var config = resource.getSpec().getConfig();
        String ingressDomain = config != null ? config.getIngressDomain() : null;
        if (ingressDomain != null) {
            var ingress = context.getSecondaryResource(Ingress.class).orElse(null);
            if (ingress != null && hasHost(ingress, ingressDomain)) {
                var cd = new ResourceStatusDetail.CustomDomainStatus();
                cd.setDomain(ingressDomain);
                cd.setStatus(eu.appbahn.shared.crd.DomainStatus.ACTIVE);
                status.setCustomDomains(List.of(cd));
            }
        }

        // Fast-fail pod-level failures instead of waiting ~120s for ProgressDeadlineExceeded.
        Optional<String> podFailure = detectPodFailure(context.getSecondaryResources(Pod.class));
        boolean conditionRolloutFailed = hasFailedRolloutCondition(depStatus);
        boolean rolloutFailed = conditionRolloutFailed || podFailure.isPresent();

        if (rolloutFailed && ready > 0) {
            status.setPhase(ResourcePhase.DEGRADED);
            status.setMessage(podFailure
                    .map(reason -> reason + " (old replicas still serving)")
                    .orElseGet(() -> "Deployment rollout failed but " + ready + " old replica(s) still running"));
            if (deploymentRevision != null) {
                status.setLatestDeploymentStatus(eu.appbahn.shared.crd.DeploymentStatus.FAILED);
            }
        } else if (ready >= desired && desired > 0) {
            status.setPhase(ResourcePhase.READY);
            status.setMessage(null);
            if (deploymentRevision != null) {
                status.setLatestDeploymentStatus(eu.appbahn.shared.crd.DeploymentStatus.SUCCEEDED);
            }
        } else if (ready > 0) {
            if (updated < desired) {
                status.setPhase(ResourcePhase.RESTARTING);
                status.setMessage("Rolling update in progress: " + ready + "/" + desired + " replicas ready");
            } else {
                status.setPhase(ResourcePhase.DEGRADED);
                status.setMessage(ready + "/" + desired + " replicas ready");
            }
            if (deploymentRevision != null) {
                status.setLatestDeploymentStatus(eu.appbahn.shared.crd.DeploymentStatus.DEPLOYING);
            }
        } else {
            if (rolloutFailed) {
                status.setPhase(ResourcePhase.ERROR);
                status.setMessage(podFailure.orElse("Deployment rollout failed"));
                if (deploymentRevision != null) {
                    status.setLatestDeploymentStatus(eu.appbahn.shared.crd.DeploymentStatus.FAILED);
                }
            } else {
                status.setPhase(ResourcePhase.PENDING);
                status.setMessage("Waiting for pods to be ready");
                if (deploymentRevision != null) {
                    status.setLatestDeploymentStatus(eu.appbahn.shared.crd.DeploymentStatus.DEPLOYING);
                }
            }
        }

        return status;
    }

    /**
     * Inspect pods belonging to this resource for terminal container failures. Returns a formatted
     * {@code "<reason>: <message>"} on the first bad pod, or empty. Pods mid-deletion are
     * skipped — their containers enter odd transient states during shutdown.
     */
    static Optional<String> detectPodFailure(Set<Pod> pods) {
        for (Pod pod : pods) {
            if (pod.getMetadata() != null && pod.getMetadata().getDeletionTimestamp() != null) {
                continue;
            }
            if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
                continue;
            }
            for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                if (cs.getState() == null || cs.getState().getWaiting() == null) {
                    continue;
                }
                String reason = cs.getState().getWaiting().getReason();
                String message = cs.getState().getWaiting().getMessage();
                if (TERMINAL_WAITING_REASONS.contains(reason)) {
                    return Optional.of(formatFailure(reason, message));
                }
                if ("CrashLoopBackOff".equals(reason)
                        && cs.getRestartCount() != null
                        && cs.getRestartCount() >= CRASHLOOP_RESTART_THRESHOLD) {
                    return Optional.of(formatFailure(reason + " (restarts=" + cs.getRestartCount() + ")", message));
                }
            }
        }
        return Optional.empty();
    }

    private static String formatFailure(String reason, String message) {
        return message != null && !message.isBlank() ? reason + ": " + message : reason;
    }

    private static boolean hasFailedRolloutCondition(io.fabric8.kubernetes.api.model.apps.DeploymentStatus depStatus) {
        if (depStatus.getConditions() == null) {
            return false;
        }
        return depStatus.getConditions().stream()
                .anyMatch(c -> "Progressing".equals(c.getType())
                        && "False".equals(c.getStatus())
                        && "ProgressDeadlineExceeded".equals(c.getReason()));
    }

    private static boolean hasHost(Ingress ingress, String expectedHost) {
        if (ingress.getSpec() == null || ingress.getSpec().getRules() == null) {
            return false;
        }
        return ingress.getSpec().getRules().stream().anyMatch(rule -> expectedHost.equals(rule.getHost()));
    }

    private static boolean statusEquals(ResourceStatusDetail a, ResourceStatusDetail b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getPhase(), b.getPhase())
                && Objects.equals(a.getMessage(), b.getMessage())
                && Objects.equals(a.getObservedGeneration(), b.getObservedGeneration())
                && Objects.equals(a.getCustomDomains(), b.getCustomDomains())
                && Objects.equals(a.getLatestDeploymentId(), b.getLatestDeploymentId())
                && Objects.equals(a.getLatestDeploymentStatus(), b.getLatestDeploymentStatus())
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
            log.debug("Synced resource {} to platform", resource.getMetadata().getName());
            if (resource.getStatus() != null) {
                resource.getStatus().setSyncFailed(false);
            }
        } catch (Exception e) {
            syncFailureCounter.increment();
            log.warn(
                    "Failed to sync resource {} to platform: {}",
                    resource.getMetadata().getName(),
                    e.getMessage());
            if (resource.getStatus() != null) {
                resource.getStatus().setSyncFailed(true);
            }
        }
    }
}
