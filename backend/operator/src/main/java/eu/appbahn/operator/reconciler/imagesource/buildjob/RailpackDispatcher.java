package eu.appbahn.operator.reconciler.imagesource.buildjob;

import eu.appbahn.shared.crd.imagesource.BuildMode;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Railpack build mode (auto-detect). Forwards source dir, image ref, and a push directive to
 * the upstream {@code railpack} CLI.
 */
@Component
public class RailpackDispatcher implements BuildModeDispatcher {

    private final BuilderConfig config;

    public RailpackDispatcher(BuilderConfig config) {
        this.config = config;
    }

    @Override
    public BuildMode mode() {
        return BuildMode.RAILPACK;
    }

    @Override
    public Container buildContainer(BuildContext ctx) {
        var builder = config.forMode(BuildMode.RAILPACK);
        List<String> entrypoint =
                builder.entrypointOrEmpty().isEmpty() ? List.of("railpack", "build") : builder.entrypointOrEmpty();
        String[] args = new String[] {ctx.workspaceMountPath(), "--name", ctx.imageRef(), "--push"};
        return new ContainerBuilder()
                .withName("railpack")
                .withImage(builder.image())
                .withImagePullPolicy("IfNotPresent")
                .withCommand(entrypoint.toArray(new String[0]))
                .withArgs(args)
                .withWorkingDir(ctx.workspaceMountPath())
                .build();
    }
}
