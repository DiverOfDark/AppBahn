package eu.appbahn.operator.reconciler;

import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceStatus;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@KubernetesDependent
public class DeploymentDependentResource extends CRUDKubernetesDependentResource<Deployment, ResourceCrd> {

    private static final Quantity DEFAULT_CPU = new Quantity("250m");
    private static final Quantity DEFAULT_MEMORY = new Quantity("256Mi");

    private final OperatorConfig operatorConfig;

    public DeploymentDependentResource(OperatorConfig operatorConfig) {
        super(Deployment.class);
        this.operatorConfig = operatorConfig;
    }

    @Override
    protected Deployment desired(ResourceCrd primary, Context<ResourceCrd> context) {
        double requestFraction = operatorConfig.getRequestFraction();
        String name = primary.getMetadata().getName();
        String namespace = primary.getMetadata().getNamespace();
        ResourceConfig config = primary.getSpec().getConfig();

        var source = config != null ? config.getSource() : null;
        var hosting = config != null ? config.getHosting() : null;

        if (!(source instanceof eu.appbahn.shared.crd.DockerSource dockerSource) || dockerSource.getImage() == null) {
            throw new IllegalStateException("Resource " + name + ": docker source with image is required");
        }

        String image = dockerSource.getImage();
        String tag = dockerSource.getTag() != null ? dockerSource.getTag() : Labels.DEFAULT_IMAGE_TAG;
        var allPorts = config.getPorts();
        Integer port = config.getLowestPort();
        int replicas;
        if (Boolean.TRUE.equals(primary.getSpec().getStopped())) {
            replicas = 0;
        } else if (isCurrentRevisionFailed(primary)) {
            // Stop K8s from crashlooping a terminally-failed revision. Recovery requires a
            // spec edit (bumps generation) or restart (bumps deploymentRevision).
            replicas = 0;
        } else {
            replicas = hosting != null && hosting.getMinReplicas() != null
                    ? hosting.getMinReplicas()
                    : Labels.DEFAULT_REPLICAS;
        }
        Quantity cpu = hosting != null && hosting.getCpu() != null ? hosting.getCpu() : DEFAULT_CPU;
        Quantity mem = hosting != null && hosting.getMemory() != null ? hosting.getMemory() : DEFAULT_MEMORY;

        Map<String, String> labels = Labels.forPrimary(primary);

        String revision = primary.getSpec().getDeploymentRevision();
        Map<String, String> podAnnotations =
                revision != null ? Map.of(Labels.DEPLOYMENT_REVISION_KEY, revision) : Map.of();

        var deploymentBuilder = new DeploymentBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withReplicas(replicas)
                .withProgressDeadlineSeconds(120)
                .withNewSelector()
                .withMatchLabels(Labels.forResource(name))
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(labels)
                .withAnnotations(podAnnotations)
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(Labels.CONTAINER_NAME)
                .withImage(image + ":" + tag)
                .withImagePullPolicy(Labels.DEFAULT_IMAGE_TAG.equals(tag) ? "Always" : "IfNotPresent")
                .withPorts(allPorts.stream()
                        .filter(p -> p.getPort() != null)
                        .map(p -> new ContainerPortBuilder()
                                .withContainerPort(p.getPort())
                                .build())
                        .toList())
                .withEnvFrom(
                        ResourceCrdUtils.hasEnvVars(primary)
                                ? List.of(new EnvFromSourceBuilder()
                                        .withNewConfigMapRef()
                                        .withName(name + "-config")
                                        .endConfigMapRef()
                                        .build())
                                : List.of())
                .withNewResources()
                .withRequests(Map.of(
                        Labels.RESOURCE_KEY_CPU, scaleQuantity(cpu, requestFraction),
                        Labels.RESOURCE_KEY_MEMORY, scaleQuantity(mem, requestFraction)))
                .withLimits(Map.of(
                        Labels.RESOURCE_KEY_CPU, cpu,
                        Labels.RESOURCE_KEY_MEMORY, mem,
                        Labels.RESOURCE_KEY_EPHEMERAL_STORAGE, new Quantity(Labels.EPHEMERAL_STORAGE_LIMIT)))
                .endResources()
                .withNewSecurityContext()
                .withRunAsNonRoot(operatorConfig.getSecurity().runAsNonRoot())
                .withReadOnlyRootFilesystem(operatorConfig.getSecurity().readOnlyRootFilesystem())
                .withAllowPrivilegeEscalation(operatorConfig.getSecurity().allowPrivilegeEscalation())
                .withNewCapabilities()
                .addToDrop(operatorConfig.getSecurity().dropCapabilities().toArray(String[]::new))
                .endCapabilities()
                .endSecurityContext()
                .endContainer()
                .withNewSecurityContext()
                .withRunAsNonRoot(operatorConfig.getSecurity().runAsNonRoot())
                .endSecurityContext()
                .endSpec()
                .endTemplate()
                .endSpec();

        addHealthProbes(deploymentBuilder, config, port);

        return deploymentBuilder.build();
    }

    private void addHealthProbes(DeploymentBuilder builder, ResourceConfig config, Integer port) {
        var healthCheck = config != null ? config.getHealthCheck() : null;

        if (healthCheck != null) {
            if (healthCheck.getReadiness() != null) {
                var k8sProbe = buildProbe(healthCheck.getReadiness(), port);
                builder.editSpec()
                        .editTemplate()
                        .editSpec()
                        .editFirstContainer()
                        .withReadinessProbe(k8sProbe)
                        .endContainer()
                        .endSpec()
                        .endTemplate()
                        .endSpec();
            }
            if (healthCheck.getLiveness() != null) {
                var k8sProbe = buildProbe(healthCheck.getLiveness(), port);
                builder.editSpec()
                        .editTemplate()
                        .editSpec()
                        .editFirstContainer()
                        .withLivenessProbe(k8sProbe)
                        .endContainer()
                        .endSpec()
                        .endTemplate()
                        .endSpec();
            }
            if (healthCheck.getStartup() != null) {
                var k8sProbe = buildProbe(healthCheck.getStartup(), port);
                builder.editSpec()
                        .editTemplate()
                        .editSpec()
                        .editFirstContainer()
                        .withStartupProbe(k8sProbe)
                        .endContainer()
                        .endSpec()
                        .endTemplate()
                        .endSpec();
            }
        } else if (port != null) {
            builder.editSpec()
                    .editTemplate()
                    .editSpec()
                    .editFirstContainer()
                    .withNewReadinessProbe()
                    .withNewTcpSocket()
                    .withPort(new IntOrString(port))
                    .endTcpSocket()
                    .withInitialDelaySeconds(5)
                    .withPeriodSeconds(10)
                    .withFailureThreshold(3)
                    .endReadinessProbe()
                    .endContainer()
                    .endSpec()
                    .endTemplate()
                    .endSpec();
        }
    }

    /** Defaults to tcpSocket on the container port when no action is configured. */
    private io.fabric8.kubernetes.api.model.Probe buildProbe(ResourceConfig.Probe probeConfig, Integer defaultPort) {
        var probeBuilder = new io.fabric8.kubernetes.api.model.ProbeBuilder();

        if (probeConfig.getHttpGet() != null) {
            var httpGet = probeConfig.getHttpGet();
            int port = httpGet.getPort() != null ? httpGet.getPort() : (defaultPort != null ? defaultPort : 8080);
            String path = httpGet.getPath() != null ? httpGet.getPath() : "/";
            probeBuilder
                    .withNewHttpGet()
                    .withPath(path)
                    .withPort(new IntOrString(port))
                    .endHttpGet();
        } else if (probeConfig.getExec() != null && probeConfig.getExec().getCommand() != null) {
            probeBuilder
                    .withNewExec()
                    .withCommand(probeConfig.getExec().getCommand())
                    .endExec();
        } else {
            int port;
            if (probeConfig.getTcpSocket() != null && probeConfig.getTcpSocket().getPort() != null) {
                port = probeConfig.getTcpSocket().getPort();
            } else {
                port = defaultPort != null ? defaultPort : 8080;
            }
            probeBuilder.withNewTcpSocket().withPort(new IntOrString(port)).endTcpSocket();
        }

        if (probeConfig.getInitialDelaySeconds() != null) {
            probeBuilder.withInitialDelaySeconds(probeConfig.getInitialDelaySeconds());
        }
        if (probeConfig.getPeriodSeconds() != null) {
            probeBuilder.withPeriodSeconds(probeConfig.getPeriodSeconds());
        }
        if (probeConfig.getFailureThreshold() != null) {
            probeBuilder.withFailureThreshold(probeConfig.getFailureThreshold());
        }

        return probeBuilder.build();
    }

    private static Quantity scaleQuantity(Quantity q, double factor) {
        String format = q.getFormat();
        BigDecimal amount = new BigDecimal(q.getAmount());
        var scaled = amount.multiply(BigDecimal.valueOf(factor));
        return new Quantity(scaled.stripTrailingZeros().toPlainString() + (format != null ? format : ""));
    }

    /** ERROR + zero ready replicas observed for the current spec generation. */
    static boolean isCurrentRevisionFailed(ResourceCrd primary) {
        ResourceStatus status = primary.getStatus();
        if (status == null || status.getPhase() != ResourcePhase.ERROR) {
            return false;
        }
        Long observed = status.getObservedGeneration();
        Long current = primary.getMetadata() != null ? primary.getMetadata().getGeneration() : null;
        if (observed == null || current == null || !observed.equals(current)) {
            return false;
        }
        ResourceStatus.ReplicaStatus replicas = status.getReplicas();
        return replicas == null || replicas.getReady() == 0;
    }
}
