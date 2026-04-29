package eu.appbahn.operator.reconciler.imagesource.buildjob;

import eu.appbahn.shared.crd.imagesource.BuildMode;
import io.fabric8.kubernetes.api.model.Container;

/**
 * Per-mode builder for the build container that runs after the init-container has cloned the
 * source. Each implementation produces the container spec; the surrounding Job (init container,
 * volume, RBAC, owner refs) is identical across modes and assembled by {@link BuildJobBuilder}.
 */
public interface BuildModeDispatcher {

    BuildMode mode();

    Container buildContainer(BuildContext ctx);
}
