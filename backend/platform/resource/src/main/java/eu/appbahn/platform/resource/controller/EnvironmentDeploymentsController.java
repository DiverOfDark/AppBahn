package eu.appbahn.platform.resource.controller;

import eu.appbahn.platform.api.environment.EnvironmentDeploymentsApi;
import eu.appbahn.platform.api.resource.PagedDeploymentResponse;
import eu.appbahn.platform.common.security.AuthContextHolder;
import eu.appbahn.platform.resource.service.DeploymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EnvironmentDeploymentsController implements EnvironmentDeploymentsApi {

    private final DeploymentService deploymentService;

    public EnvironmentDeploymentsController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @Override
    public ResponseEntity<PagedDeploymentResponse> listEnvironmentDeployments(String slug, Integer limit) {
        return ResponseEntity.ok(deploymentService.listByEnvironment(slug, limit, AuthContextHolder.get()));
    }
}
