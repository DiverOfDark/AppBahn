package eu.appbahn.operator.reconciler.imagesource.buildjob;

import eu.appbahn.shared.crd.imagesource.BuildMode;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Nixpacks build mode (auto-detect). Wraps the upstream {@code nixpacks build} with a push
 * step. No per-source options — Nixpacks owns the entire detect→build pipeline.
 */
@Component
public class NixpacksDispatcher implements BuildModeDispatcher {

    private final BuilderConfig config;

    public NixpacksDispatcher(BuilderConfig config) {
        this.config = config;
    }

    @Override
    public BuildMode mode() {
        return BuildMode.NIXPACKS;
    }

    @Override
    public Container buildContainer(BuildContext ctx) {
        var builder = config.forMode(BuildMode.NIXPACKS);
        List<String> entrypoint =
                builder.entrypointOrEmpty().isEmpty() ? List.of("nixpacks", "build") : builder.entrypointOrEmpty();
        String[] args = new String[] {ctx.workspaceMountPath(), "--name", ctx.imageRef(), "--push"};
        return new ContainerBuilder()
                .withName("nixpacks")
                .withImage(builder.image())
                .withImagePullPolicy("IfNotPresent")
                .withCommand(entrypoint.toArray(new String[0]))
                .withArgs(args)
                .withWorkingDir(ctx.workspaceMountPath())
                .build();
    }
}
