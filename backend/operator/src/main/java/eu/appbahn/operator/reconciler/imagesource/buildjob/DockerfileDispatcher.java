package eu.appbahn.operator.reconciler.imagesource.buildjob;

import eu.appbahn.shared.crd.imagesource.BuildMode;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Dockerfile build via {@code buildctl-daemonless.sh} (rootless Buildkit). The standard
 * Buildkit pattern: solver=dockerfile, frontend=dockerfile.v0, output=image push.
 */
@Component
public class DockerfileDispatcher implements BuildModeDispatcher {

    private final BuilderConfig config;

    public DockerfileDispatcher(BuilderConfig config) {
        this.config = config;
    }

    @Override
    public BuildMode mode() {
        return BuildMode.DOCKERFILE;
    }

    @Override
    public Container buildContainer(BuildContext ctx) {
        var builder = config.forMode(BuildMode.DOCKERFILE);
        String dockerfilePath = "Dockerfile";
        String contextPath = ".";
        if (ctx.source().getSpec().getBuild() != null
                && ctx.source().getSpec().getBuild().getDockerfile() != null) {
            var df = ctx.source().getSpec().getBuild().getDockerfile();
            if (df.getPath() != null && !df.getPath().isBlank()) {
                dockerfilePath = df.getPath();
            }
            if (df.getContext() != null && !df.getContext().isBlank()) {
                contextPath = df.getContext();
            }
        }

        List<String> entrypoint =
                builder.entrypointOrEmpty().isEmpty() ? List.of("buildctl-daemonless.sh") : builder.entrypointOrEmpty();
        String[] args = new String[] {
            "build",
            "--frontend",
            "dockerfile.v0",
            "--local",
            "context=" + ctx.workspaceMountPath() + "/" + contextPath,
            "--local",
            "dockerfile=" + ctx.workspaceMountPath(),
            "--opt",
            "filename=" + dockerfilePath,
            "--output",
            "type=image,name=" + ctx.imageRef() + ",push=true,registry.insecure="
                    + (ctx.registryInsecure() ? "true" : "false")
        };
        return new ContainerBuilder()
                .withName("dockerfile")
                .withImage(builder.image())
                .withImagePullPolicy("IfNotPresent")
                .withCommand(entrypoint.toArray(new String[0]))
                .withArgs(args)
                .withWorkingDir(ctx.workspaceMountPath())
                .build();
    }
}
