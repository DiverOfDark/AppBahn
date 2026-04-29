package eu.appbahn.operator.reconciler.imagesource.buildjob;

import eu.appbahn.shared.crd.imagesource.BuildMode;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Cloud Native Buildpacks via {@code pack build}. Builder image (paketobuildpacks /
 * heroku-buildpacks / etc.) is helm-driven; the user can override per-source via
 * {@code spec.build.buildpack.builder}.
 */
@Component
public class BuildpackDispatcher implements BuildModeDispatcher {

    private final BuilderConfig config;

    public BuildpackDispatcher(BuilderConfig config) {
        this.config = config;
    }

    @Override
    public BuildMode mode() {
        return BuildMode.BUILDPACK;
    }

    @Override
    public Container buildContainer(BuildContext ctx) {
        var builder = config.forMode(BuildMode.BUILDPACK);
        String packBuilder = null;
        if (ctx.source().getSpec().getBuild() != null
                && ctx.source().getSpec().getBuild().getBuildpack() != null) {
            packBuilder = ctx.source().getSpec().getBuild().getBuildpack().getBuilder();
        }
        List<String> entrypoint =
                builder.entrypointOrEmpty().isEmpty() ? List.of("pack", "build") : builder.entrypointOrEmpty();
        String[] args;
        if (packBuilder != null && !packBuilder.isBlank()) {
            args = new String[] {
                ctx.imageRef(), "--builder", packBuilder, "--path", ctx.workspaceMountPath(), "--publish"
            };
        } else {
            args = new String[] {ctx.imageRef(), "--path", ctx.workspaceMountPath(), "--publish"};
        }
        return new ContainerBuilder()
                .withName("buildpack")
                .withImage(builder.image())
                .withImagePullPolicy("IfNotPresent")
                .withCommand(entrypoint.toArray(new String[0]))
                .withArgs(args)
                .withWorkingDir(ctx.workspaceMountPath())
                .build();
    }
}
