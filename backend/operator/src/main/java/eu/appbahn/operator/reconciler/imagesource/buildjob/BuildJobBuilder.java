package eu.appbahn.operator.reconciler.imagesource.buildjob;

import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.imagesource.BuildMode;
import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;
import eu.appbahn.shared.crd.imagesource.ImageSourceGitSpec;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Assembles the K8s Job for a single build: ownership-referenced to the ImageSource, init
 * container clones git, builder container dispatches by mode and pushes to the configured
 * registry.
 */
@Component
public class BuildJobBuilder {

    /** Where the init container drops the cloned source; the builder container reads from here. */
    public static final String WORKSPACE_MOUNT = "/workspace";

    /** Volume name for the workspace emptyDir. */
    public static final String WORKSPACE_VOLUME = "source";

    private final BuilderConfig config;
    private final Map<BuildMode, BuildModeDispatcher> dispatchers;

    public BuildJobBuilder(BuilderConfig config, List<BuildModeDispatcher> dispatcherList) {
        this.config = config;
        this.dispatchers = new HashMap<>();
        for (BuildModeDispatcher d : dispatcherList) {
            this.dispatchers.put(d.mode(), d);
        }
    }

    public Job build(ImageSourceCrd source, String sourceCommit, String deploymentId, String jobName) {
        BuildMode mode = source.getSpec().getBuild() != null
                        && source.getSpec().getBuild().getMode() != null
                ? source.getSpec().getBuild().getMode()
                : BuildMode.PEELBOX;
        BuildModeDispatcher dispatcher = dispatchers.get(mode);
        if (dispatcher == null) {
            throw new IllegalStateException("no dispatcher registered for build mode " + mode);
        }

        String imageRef = imageRefFor(source, sourceCommit);
        BuildContext ctx = new BuildContext(
                source, sourceCommit, imageRef, config.registry(), config.registryInsecure(), WORKSPACE_MOUNT);

        Container builder = dispatcher.buildContainer(ctx);
        Container clone = buildCloneContainer(source, sourceCommit);

        Map<String, String> labels = new HashMap<>();
        labels.put(Labels.MANAGED_BY_KEY, Labels.MANAGED_BY_VALUE);
        labels.put(Labels.IMAGE_SOURCE_KEY, source.getMetadata().getName());
        labels.put(Labels.BUILD_MODE_KEY, mode.name().toLowerCase());
        if (sourceCommit != null) {
            labels.put(Labels.BUILD_COMMIT_KEY, shorten(sourceCommit, 12));
        }
        if (deploymentId != null) {
            labels.put(Labels.BUILD_DEPLOYMENT_ID_KEY, deploymentId);
        }

        Volume workspace = new VolumeBuilder()
                .withName(WORKSPACE_VOLUME)
                .withEmptyDir(new EmptyDirVolumeSource())
                .build();
        VolumeMount workspaceMount = new VolumeMountBuilder()
                .withName(WORKSPACE_VOLUME)
                .withMountPath(WORKSPACE_MOUNT)
                .build();

        // Append the workspace mount alongside any per-container mounts the dispatcher already
        // added (e.g. git-creds on the clone container). Don't overwrite — the clone container
        // already mounts the credentials volume.
        if (clone.getVolumeMounts() == null) {
            clone.setVolumeMounts(new ArrayList<>());
        }
        clone.getVolumeMounts().add(workspaceMount);
        if (builder.getVolumeMounts() == null) {
            builder.setVolumeMounts(new ArrayList<>());
        }
        builder.getVolumeMounts().add(workspaceMount);

        var podSpecBuilder = new PodSpecBuilder()
                .withRestartPolicy("Never")
                .withInitContainers(clone)
                .withContainers(builder);
        List<Volume> volumes = new ArrayList<>();
        volumes.add(workspace);
        ImageSourceGitSpec gitSpec = source.getSpec().getGit();
        if (gitSpec != null
                && gitSpec.getCredentialsSecretRef() != null
                && !gitSpec.getCredentialsSecretRef().isBlank()) {
            volumes.add(new VolumeBuilder()
                    .withName("git-creds")
                    .withNewSecret()
                    .withSecretName(gitSpec.getCredentialsSecretRef())
                    .endSecret()
                    .build());
        }
        podSpecBuilder.withVolumes(volumes);
        PodSpec podSpec = podSpecBuilder.build();

        PodTemplateSpec template = new PodTemplateSpecBuilder()
                .withNewMetadata()
                .withLabels(labels)
                .endMetadata()
                .withSpec(podSpec)
                .build();

        OwnerReference ownerRef = new OwnerReference();
        ownerRef.setApiVersion(source.getApiVersion());
        ownerRef.setKind(source.getKind());
        ownerRef.setName(source.getMetadata().getName());
        ownerRef.setUid(source.getMetadata().getUid());
        ownerRef.setController(true);
        ownerRef.setBlockOwnerDeletion(true);

        return new JobBuilder()
                .withNewMetadata()
                .withName(jobName)
                .withNamespace(source.getMetadata().getNamespace())
                .withLabels(labels)
                .withOwnerReferences(ownerRef)
                .endMetadata()
                .withNewSpec()
                .withBackoffLimit(0)
                .withTtlSecondsAfterFinished(config.ttlSecondsAfterFinished())
                .withActiveDeadlineSeconds((long) config.activeDeadlineSeconds())
                .withTemplate(template)
                .endSpec()
                .build();
    }

    /**
     * Standard AppBahn image ref shape: {@code <registry>/<namespace>/<name>:<commit>}.
     * Namespace + name disambiguate across environments; commit gives every build a unique
     * tag so registry GC can reason about lifecycle.
     */
    public String imageRefFor(ImageSourceCrd source, String sourceCommit) {
        String tag = sourceCommit != null && sourceCommit.length() > 12 ? sourceCommit.substring(0, 12) : sourceCommit;
        if (tag == null || tag.isBlank()) {
            tag = Labels.DEFAULT_IMAGE_TAG;
        }
        return config.registry() + "/" + source.getMetadata().getNamespace() + "/"
                + source.getMetadata().getName() + ":" + tag;
    }

    private Container buildCloneContainer(ImageSourceCrd source, String sourceCommit) {
        ImageSourceGitSpec git = source.getSpec().getGit();
        if (git == null) {
            throw new IllegalStateException(
                    "git spec required to build " + source.getMetadata().getName());
        }
        String repo = git.getRepo();
        String branch = git.getBranch() == null || git.getBranch().isBlank() ? "main" : git.getBranch();
        String secretName = git.getCredentialsSecretRef();

        // Use the same alpine/git image as the e2e test git server. If the secret is set, the
        // init container reads username/password and constructs an authenticated URL on the
        // fly. Plaintext credentials never leave the pod.
        String script;
        if (secretName != null && !secretName.isBlank()) {
            script = "set -eu\n"
                    + "USER=\"$(cat /git-creds/username)\"\n"
                    + "PASS=\"$(cat /git-creds/password)\"\n"
                    + "AUTH_REPO=\"$(printf '%s' \"" + repo + "\" | sed -E 's#^(https?://)#\\1'\"$USER:$PASS\"'@#')\"\n"
                    + "git clone --branch " + branch + " --single-branch \"$AUTH_REPO\" " + WORKSPACE_MOUNT + "\n"
                    + "cd " + WORKSPACE_MOUNT + "\n"
                    + "git checkout " + sourceCommit;
        } else {
            script = "set -eu\n"
                    + "git clone --branch " + branch + " --single-branch '" + repo + "' " + WORKSPACE_MOUNT + "\n"
                    + "cd " + WORKSPACE_MOUNT + "\n"
                    + "git checkout " + sourceCommit;
        }

        var b = new ContainerBuilder()
                .withName("git-clone")
                .withImage(config.gitCloneImage())
                .withImagePullPolicy("IfNotPresent")
                .withCommand("sh", "-c", script);
        if (secretName != null && !secretName.isBlank()) {
            b.addNewVolumeMount()
                    .withName("git-creds")
                    .withMountPath("/git-creds")
                    .withReadOnly(true)
                    .endVolumeMount();
        }
        return b.build();
    }

    private static String shorten(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
