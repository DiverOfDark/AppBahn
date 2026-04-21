package eu.appbahn.platform.workspace.service;

/** Resolves the target cluster name for a new environment. Implemented by the tunnel
 * module, which owns the {@code cluster} table populated via operator registration. */
public interface TargetClusterResolver {

    /**
     * Resolve the target cluster name for a new environment.
     *
     * @param requested explicit cluster name from the request, or {@code null} to auto-pick
     * @return the name of an approved cluster
     * @throws eu.appbahn.platform.common.exception.ValidationException when
     *     {@code requested} is not registered/approved, or when auto-pick can't choose
     *     (zero or more than one approved cluster)
     */
    String resolve(String requested);
}
