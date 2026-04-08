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
import eu.appbahn.platform.common.security.AuthContextHolder;
import eu.appbahn.platform.workspace.service.EnvironmentService;
import eu.appbahn.platform.workspace.service.EnvironmentTokenService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EnvironmentsController implements EnvironmentsApi {

    private final EnvironmentService environmentService;
    private final EnvironmentTokenService tokenService;

    public EnvironmentsController(EnvironmentService environmentService, EnvironmentTokenService tokenService) {
        this.environmentService = environmentService;
        this.tokenService = tokenService;
    }

    @Override
    public ResponseEntity<Environment> createEnvironment(CreateEnvironmentRequest createEnvironmentRequest) {
        var env = environmentService.create(createEnvironmentRequest, AuthContextHolder.get());
        return ResponseEntity.status(201).body(env);
    }

    @Override
    public ResponseEntity<PagedEnvironmentResponse> listEnvironments(
            String projectSlug, Integer page, Integer size, String sort) {
        var result = environmentService.list(projectSlug, page, size, sort, AuthContextHolder.get());
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<Environment> getEnvironment(String slug) {
        return ResponseEntity.ok(environmentService.getBySlug(slug, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<Environment> updateEnvironment(
            String slug, UpdateEnvironmentRequest updateEnvironmentRequest) {
        var env = environmentService.update(slug, updateEnvironmentRequest, AuthContextHolder.get());
        return ResponseEntity.ok(env);
    }

    @Override
    public ResponseEntity<Void> deleteEnvironment(String slug) {
        environmentService.delete(slug, AuthContextHolder.get());
        return ResponseEntity.noContent().build();
    }

    // --- Environment tokens ---

    @Override
    public ResponseEntity<List<EnvironmentToken>> listEnvironmentTokens(String slug) {
        return ResponseEntity.ok(tokenService.listTokens(slug, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<CreateEnvironmentTokenResponse> createEnvironmentToken(
            String slug, CreateEnvironmentTokenRequest createEnvironmentTokenRequest) {
        return ResponseEntity.status(201)
                .body(tokenService.createToken(slug, createEnvironmentTokenRequest, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<Void> deleteEnvironmentToken(String slug, UUID tokenId) {
        tokenService.deleteToken(slug, tokenId, AuthContextHolder.get());
        return ResponseEntity.noContent().build();
    }

    // --- Not implemented in Sprint 3 ---

    @Override
    public ResponseEntity<Quota> getEnvironmentQuota(String slug) {
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
}
