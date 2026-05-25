package eu.appbahn.platform.resource.stats;

import java.util.UUID;

/**
 * One row of {@link StatsRepository#environmentStatusRollupsForWorkspace}: project + env slug
 * + worst-rank aggregate status across that env's resources. {@code status} is {@code null}
 * for environments with zero resources.
 */
public record EnvironmentStatusRollupRow(UUID projectId, String slug, String status) {}
