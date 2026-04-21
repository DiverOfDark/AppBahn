package eu.appbahn.platform.tunnel.registration;

import eu.appbahn.platform.tunnel.cluster.ClusterStatus;

public record ClusterRegistrationResponse(String clusterName, ClusterStatus status, String fingerprint) {}
