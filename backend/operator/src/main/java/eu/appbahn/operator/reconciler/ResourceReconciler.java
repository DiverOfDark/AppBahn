package eu.appbahn.operator.reconciler;

import eu.appbahn.operator.client.api.ResourceSyncApi;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceStatus;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Objects;
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
                    reconcilePrecondition = DockerSourceDefinedCondition.class),
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

    private final ResourceSyncApi resourceSyncApi;
    private final OperatorConfig operatorConfig;
    private final long rescheduleSeconds;
    private final Counter syncFailureCounter;

    public ResourceReconciler(
            ResourceSyncApi resourceSyncApi,
            OperatorConfig operatorConfig,
            MeterRegistry meterRegistry,
            @org.springframework.beans.factory.annotation.Value("${operator.reschedule-seconds:300}")
                    long rescheduleSeconds) {
        this.resourceSyncApi = resourceSyncApi;
        this.operatorConfig = operatorConfig;
        this.rescheduleSeconds = rescheduleSeconds;
        this.syncFailureCounter = Counter.builder("appbahn.operator.sync.failures")
                .description("Number of failed platform sync attempts")
                .register(meterRegistry);
    }

    @Override
    public UpdateControl<ResourceCrd> reconcile(ResourceCrd resource, Context<ResourceCrd> context) {
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        log.info("Reconciling Resource: {} in namespace {}", name, namespace);

        try {
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
            resourceSyncApi.deleteResourceSync(name);
            log.info("Notified platform of resource deletion: {}", name);
        } catch (Exception e) {
            log.warn("Failed to notify platform of deletion for {}: {}", name, e.getMessage());
        }

        return DeleteControl.defaultDelete();
    }

    private ResourceStatus createErrorStatus(ResourceCrd resource, String message) {
        var status = new ResourceStatus();
        status.setPhase(ResourcePhase.ERROR);
        status.setMessage(message);
        status.setObservedGeneration(resource.getMetadata().getGeneration());
        return status;
    }

    private ResourceStatus deriveStatus(ResourceCrd resource, Deployment k8sDeployment, Context<ResourceCrd> context) {
        var status = new ResourceStatus();
        status.setObservedGeneration(resource.getMetadata().getGeneration());
        // Preserve syncFailed from previous status — it's managed by syncToPlatform, not derived from K8s
        if (resource.getStatus() != null) {
            status.setSyncFailed(resource.getStatus().getSyncFailed());
        }

        // Track the deployment being processed (from spec.deploymentRevision)
        String deploymentRevision = resource.getSpec().getDeploymentRevision();
        if (deploymentRevision != null) {
            status.setLatestDeploymentId(deploymentRevision);
        }

        if (k8sDeployment == null || k8sDeployment.getStatus() == null) {
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

        if (Boolean.TRUE.equals(resource.getSpec().getStopped())) {
            if (ready == 0) {
                status.setPhase(ResourcePhase.STOPPED);
                status.setMessage("Resource is stopped");
            } else {
                status.setPhase(ResourcePhase.PENDING);
                status.setMessage("Stopping...");
            }
            var replicas = new ResourceStatus.ReplicaStatus();
            replicas.setDesired(desired);
            replicas.setReady(ready);
            replicas.setUpdated(updated);
            replicas.setAvailable(available);
            status.setReplicas(replicas);
            return status;
        }

        var replicas = new ResourceStatus.ReplicaStatus();
        replicas.setDesired(desired);
        replicas.setReady(ready);
        replicas.setUpdated(updated);
        replicas.setAvailable(available);
        status.setReplicas(replicas);

        // Derive domains from Ingress secondary resource, not spec echo
        var config = resource.getSpec().getConfig();
        String ingressDomain = config != null ? config.getIngressDomain() : null;
        if (ingressDomain != null) {
            var ingress = context.getSecondaryResource(Ingress.class).orElse(null);
            if (ingress != null && hasHost(ingress, ingressDomain)) {
                var cd = new ResourceStatus.CustomDomainStatus();
                cd.setDomain(ingressDomain);
                cd.setStatus(eu.appbahn.shared.crd.DomainStatus.ACTIVE);
                status.setCustomDomains(List.of(cd));
            }
        }

        if (ready >= desired && desired > 0) {
            status.setPhase(ResourcePhase.READY);
            status.setMessage(null);
            if (deploymentRevision != null) {
                status.setLatestDeploymentStatus(eu.appbahn.shared.crd.DeploymentStatus.SUCCEEDED);
            }
        } else if (ready > 0) {
            // H1: Check if a rolling update is in progress
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
            // Zero ready replicas — check for rollout failure via K8s conditions
            boolean rolloutFailed = hasFailedRolloutCondition(depStatus);
            if (rolloutFailed) {
                status.setPhase(ResourcePhase.ERROR);
                status.setMessage("Deployment rollout failed");
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

    private static boolean statusEquals(ResourceStatus a, ResourceStatus b) {
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

    private static boolean replicasEqual(ResourceStatus.ReplicaStatus a, ResourceStatus.ReplicaStatus b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.getDesired() == b.getDesired()
                && a.getReady() == b.getReady()
                && a.getUpdated() == b.getUpdated()
                && a.getAvailable() == b.getAvailable();
    }

    private void syncToPlatform(ResourceCrd resource) {
        try {
            var syncRequest = ResourceSyncRequestBuilder.fromCrd(resource, operatorConfig.getClusterName());
            resourceSyncApi.syncResource(syncRequest);
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
