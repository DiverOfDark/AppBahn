package eu.appbahn.platform.resource.service;

/**
 * The result of resolving an image reference against a registry. {@code repo} already includes any
 * namespace (e.g. {@code library/nginx}); {@code digest} is the {@code Docker-Content-Digest}
 * header value (e.g. {@code sha256:abc...}).
 */
public record ResolvedImage(String registry, String repo, String tag, String digest) {

    /** {@code repo@digest} — the digest-pinned ref to use in the Pod spec. */
    public String digestPinnedRef(String displayImage) {
        return displayImage + "@" + digest;
    }
}
