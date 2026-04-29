package eu.appbahn.operator.reconciler.imagesource.buildjob;

import eu.appbahn.shared.crd.imagesource.ImageSourceCrd;

/**
 * Inputs for a single build Job. {@code imageRef} is the fully-qualified target ref the build
 * is expected to push to ({@code registry/path:commit}). {@code workspaceMountPath} is where
 * the init container delivers the cloned source to the builder container.
 */
public record BuildContext(
        ImageSourceCrd source,
        String sourceCommit,
        String imageRef,
        String registry,
        boolean registryInsecure,
        String workspaceMountPath) {}
