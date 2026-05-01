package eu.appbahn.platform.resource.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Retention policy for the {@code deployment} audit table. Without one, the table grows unbounded
 * — every build, restart, env edit, promote, rollback, and unpin appends a row, and rollback's
 * "previous successful" lookup walks an unbounded list. Helm wires env vars (e.g.
 * {@code PLATFORM_DEPLOYMENT_RETENTION_MAXBUILDSPERRESOURCE}) into Spring's relaxed binding.
 *
 * <p>Eligibility is enforced in {@link DeploymentRetentionService}: only terminal-state rows
 * (SUPERSEDED / FAILED / CANCELED) older than the most-recent {@code maxBuildsPerResource} per
 * Resource are pruned, and rows referenced by any Resource's {@code spec.pinnedRelease} are kept
 * regardless of age so rollback never loses its target.
 */
@ConfigurationProperties(prefix = "platform.deployment.retention")
public record DeploymentRetentionProperties(
        @DefaultValue("10") int maxBuildsPerResource,
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0 0 3 * * *") String scheduleCron) {}
