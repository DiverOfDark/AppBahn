package eu.appbahn.operator.reconciler.imagesource.buildjob;

import eu.appbahn.shared.crd.imagesource.BuildMode;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Peelbox build mode — the default. Auto-detects the project type and produces a Buildkit-
 * compatible image, pushing directly to the configured registry. The actual buildkit binary
 * is bundled in the helm-configured peelbox image.
 */
@Component
public class PeelboxDispatcher implements BuildModeDispatcher {

    private final BuilderConfig config;

    public PeelboxDispatcher(BuilderConfig config) {
        this.config = config;
    }

    @Override
    public BuildMode mode() {
        return BuildMode.PEELBOX;
    }

    @Override
    public Container buildContainer(BuildContext ctx) {
        var builder = config.forMode(BuildMode.PEELBOX);
        List<String> args = new ArrayList<>();
        if (builder.entrypointOrEmpty().isEmpty()) {
            // Conventional peelbox CLI form. Helm-overridable.
            args.add("peelbox");
            args.add("build");
        } else {
            args.addAll(builder.entrypointOrEmpty());
        }
        args.add("--source");
        args.add(ctx.workspaceMountPath());
        args.add("--push");
        args.add(ctx.imageRef());
        if (ctx.registryInsecure()) {
            args.add("--insecure-registry");
            args.add(ctx.registry());
        }

        return new ContainerBuilder()
                .withName("peelbox")
                .withImage(builder.image())
                .withImagePullPolicy("IfNotPresent")
                .withCommand(args.subList(0, 1).toArray(new String[0]))
                .withArgs(args.subList(1, args.size()).toArray(new String[0]))
                .withWorkingDir(ctx.workspaceMountPath())
                .build();
    }
}
