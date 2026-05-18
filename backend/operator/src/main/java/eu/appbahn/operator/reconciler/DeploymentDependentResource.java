package eu.appbahn.operator.reconciler;

import eu.appbahn.operator.reconciler.imagesource.ResourceReleaseResolver;
import eu.appbahn.operator.tunnel.AdminConfigCache;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.CommandOverride;
import eu.appbahn.shared.crd.DeployStrategy;
import eu.appbahn.shared.crd.NodePool;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceStatusDetail;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.TolerationBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@KubernetesDependent
public class DeploymentDependentResource extends CRUDKubernetesDependentResource<Deployment, ResourceCrd> {

    private static final Quantity DEFAULT_CPU = new Quantity("250m");
    private static final Quantity DEFAULT_MEMORY = new Quantity("256Mi");

    private final OperatorConfig operatorConfig;
    private final AdminConfigCache adminConfigCache;

    public DeploymentDependentResource(OperatorConfig operatorConfig, AdminConfigCache adminConfigCache) {
        super(Deployment.class);
        this.operatorConfig = operatorConfig;
        this.adminConfigCache = adminConfigCache;
    }

    @Override
    protected Deployment desired(ResourceCrd primary, Context<ResourceCrd> context) {
        double requestFraction = operatorConfig.getRequestFraction();
        String name = primary.getMetadata().getName();
        String namespace = primary.getMetadata().getNamespace();
        ResourceConfig config = primary.getSpec().getConfig();

        var hosting = config != null ? config.getHosting() : null;

        // Resolved by DeploymentReconcileCondition before this method runs — but resolve again
        // because the workflow precondition only checks reachability, not freshness. The bound
        // ImageSource is the sibling with the same name in the same namespace.
        String containerImage = ResourceReleaseResolver.resolveImageRef(primary, context)
                .orElseThrow(() -> new IllegalStateException(
                        "Resource " + name + ": sibling ImageSource has no latestArtifact yet"));

        var allPorts = config != null ? config.getPorts() : List.<ResourceConfig.PortConfig>of();
        Integer port = config != null ? config.getLowestPort() : null;
        int replicas;
        if (isCurrentRevisionFailed(primary)) {
            // Stop K8s from crashlooping a terminally-failed revision. Recovery requires a
            // spec edit (bumps generation) or a restartGeneration bump.
            replicas = 0;
        } else {
            replicas = hosting != null && hosting.getMinReplicas() != null
                    ? hosting.getMinReplicas()
                    : Labels.DEFAULT_REPLICAS;
        }
        Quantity cpu = hosting != null && hosting.getCpu() != null ? hosting.getCpu() : DEFAULT_CPU;
        Quantity mem = hosting != null && hosting.getMemory() != null ? hosting.getMemory() : DEFAULT_MEMORY;

        Map<String, String> labels = Labels.forPrimary(primary);

        Map<String, String> podAnnotations = buildPodAnnotations(primary, containerImage);

        CommandOverride override = primary.getSpec().getCommandOverride();
        List<String> commandOverride = override != null
                        && override.getCommand() != null
                        && !override.getCommand().isEmpty()
                ? override.getCommand()
                : null;
        List<String> argsOverride = override != null
                        && override.getArgs() != null
                        && !override.getArgs().isEmpty()
                ? override.getArgs()
                : null;

        DeployStrategy deployStrategy = hosting != null ? hosting.effectiveDeployStrategy() : DeployStrategy.ROLLING;
        Optional<NodePool> matchingPool = hosting != null && hosting.getNodePool() != null
                ? resolveNodePool(hosting.getNodePool())
                : Optional.empty();

        var deploymentBuilder = new DeploymentBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withReplicas(replicas)
                .withProgressDeadlineSeconds(120)
                .withNewStrategy()
                .withType(deployStrategy == DeployStrategy.RECREATE ? "Recreate" : "RollingUpdate")
                .endStrategy()
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
                .withImage(containerImage)
                .withImagePullPolicy("IfNotPresent")
                .withCommand(commandOverride)
                .withArgs(argsOverride)
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
        applyNodePool(deploymentBuilder, matchingPool.orElse(null));

        return deploymentBuilder.build();
    }

    private Optional<NodePool> resolveNodePool(String name) {
        return adminConfigCache.snapshot().stream()
                .flatMap(s -> {
                    List<NodePool> pools = s.getNodePools();
                    return pools == null ? java.util.stream.Stream.<NodePool>empty() : pools.stream();
                })
                .filter(p -> name.equals(p.getName()))
                .findFirst();
    }

    private void applyNodePool(DeploymentBuilder builder, NodePool pool) {
        if (pool == null) {
            return;
        }
        var spec = builder.editSpec().editTemplate().editSpec();
        if (pool.getNodeSelector() != null && !pool.getNodeSelector().isEmpty()) {
            spec.withNodeSelector(pool.getNodeSelector());
        }
        if (pool.getTolerations() != null && !pool.getTolerations().isEmpty()) {
            spec.withTolerations(pool.getTolerations().stream()
                    .map(t -> new TolerationBuilder()
                            .withKey(t.getKey())
                            .withOperator(t.getOperator())
                            .withValue(t.getValue())
                            .withEffect(t.getEffect())
                            .withTolerationSeconds(t.getTolerationSeconds())
                            .build())
                    .toList());
        }
        spec.endSpec().endTemplate().endSpec();
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
    private io.fabric8.kubernetes.api.model.Probe buildProbe(
            ResourceConfig.ProbeConfig probeConfig, Integer defaultPort) {
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

    /**
     * Pod-template annotations that drive K8s rollout when relevant inputs change. Carries the
     * resolved {@code imageRef} (digest-pinned) so the bound ImageSource's new artifact triggers
     * a roll, plus {@code restartGeneration} so an explicit restart bump triggers a roll without
     * changing the image.
     */
    private static Map<String, String> buildPodAnnotations(ResourceCrd primary, String imageRef) {
        Map<String, String> annotations = new HashMap<>();
        annotations.put(Labels.RELEASE_IMAGE_REF_KEY, imageRef);
        Long restartGen = primary.getSpec().getRestartGeneration();
        if (restartGen != null) {
            annotations.put(Labels.RESTART_GENERATION_KEY, restartGen.toString());
        }
        return annotations;
    }

    /** ERROR + zero ready replicas observed for the current spec generation. */
    static boolean isCurrentRevisionFailed(ResourceCrd primary) {
        ResourceStatusDetail status = primary.getStatus();
        if (status == null || status.getPhase() != ResourcePhase.ERROR) {
            return false;
        }
        Long observed = status.getObservedGeneration();
        Long current = primary.getMetadata() != null ? primary.getMetadata().getGeneration() : null;
        if (observed == null || current == null || !observed.equals(current)) {
            return false;
        }
        ResourceStatusDetail.ReplicaStatus replicas = status.getReplicas();
        return replicas == null || replicas.getReady() == 0;
    }
}
