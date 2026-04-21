package eu.appbahn.platform.workspace.service;

/** Abstraction over Kubernetes namespace operations. The real implementation enqueues
 * tunnel commands (handled in the operator); the no-op fallback lets unit tests run
 * without a tunnel wiring. */
public interface NamespaceCrdClient {

    /** Apply a namespace for the given environment slug. Idempotent (server-side apply). */
    void apply(String environmentSlug, String namespace);

    /** Delete a namespace by name. Tolerates the namespace already being absent. */
    void delete(String namespace);
}
