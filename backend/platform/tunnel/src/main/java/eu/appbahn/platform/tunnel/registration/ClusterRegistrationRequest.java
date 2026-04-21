package eu.appbahn.platform.tunnel.registration;

import jakarta.validation.constraints.NotBlank;

public record ClusterRegistrationRequest(
        @NotBlank String clusterName, @NotBlank String publicKey, String operatorVersion, String operatorInstanceId) {}
