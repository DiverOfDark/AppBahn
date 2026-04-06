package eu.appbahn.platform.workspace.controller;

import eu.appbahn.platform.api.EnvironmentsApi;
import eu.appbahn.platform.api.model.ApprovalGatesConfig;
import eu.appbahn.platform.api.model.CreateEnvironmentRequest;
import eu.appbahn.platform.api.model.CreateEnvironmentTokenRequest;
import eu.appbahn.platform.api.model.CreateEnvironmentTokenResponse;
import eu.appbahn.platform.api.model.Environment;
import eu.appbahn.platform.api.model.EnvironmentToken;
import eu.appbahn.platform.api.model.PagedEnvironmentResponse;
import eu.appbahn.platform.api.model.Quota;
import eu.appbahn.platform.api.model.RegistryConfig;
import eu.appbahn.platform.api.model.SetTargetClusterRequest;
import eu.appbahn.platform.api.model.UpdateEnvironmentRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class EnvironmentsController implements EnvironmentsApi {

    @Override
    public ResponseEntity<Environment> createEnvironment(CreateEnvironmentRequest createEnvironmentRequest) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<CreateEnvironmentTokenResponse> createEnvironmentToken(String slug, CreateEnvironmentTokenRequest createEnvironmentTokenRequest) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Void> deleteEnvironment(String slug) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Void> deleteEnvironmentToken(String slug, UUID tokenId) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Environment> getEnvironment(String slug) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Quota> getEnvironmentQuota(String slug) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<List<EnvironmentToken>> listEnvironmentTokens(String slug) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<PagedEnvironmentResponse> listEnvironments(String projectSlug, Integer page, Integer size, String sort) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Environment> setApprovalGates(String slug, ApprovalGatesConfig approvalGatesConfig) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Quota> setEnvironmentQuota(String slug, Quota quota) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Environment> setEnvironmentRegistry(String slug, RegistryConfig registryConfig) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Environment> setTargetCluster(String slug, SetTargetClusterRequest setTargetClusterRequest) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Environment> updateEnvironment(String slug, UpdateEnvironmentRequest updateEnvironmentRequest) {
        return ResponseEntity.status(501).build();
    }
}
