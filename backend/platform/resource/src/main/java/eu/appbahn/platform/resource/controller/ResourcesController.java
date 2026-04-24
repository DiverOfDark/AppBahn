package eu.appbahn.platform.resource.controller;

import eu.appbahn.platform.api.ResourcesApi;
import eu.appbahn.platform.api.model.CreateResourceRequest;
import eu.appbahn.platform.api.model.Deployment;
import eu.appbahn.platform.api.model.PagedDeploymentResponse;
import eu.appbahn.platform.api.model.PagedResourceResponse;
import eu.appbahn.platform.api.model.Resource;
import eu.appbahn.platform.api.model.ResourceCreatedResponse;
import eu.appbahn.platform.api.model.TriggerDeploymentRequest;
import eu.appbahn.platform.api.model.TriggerDeploymentResponse;
import eu.appbahn.platform.api.model.UpdateResourceRequest;
import eu.appbahn.platform.common.security.AuthContextHolder;
import eu.appbahn.platform.resource.service.DeploymentService;
import eu.appbahn.platform.resource.service.ResourceLifecycleService;
import eu.appbahn.platform.resource.service.ResourceService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ResourcesController implements ResourcesApi {

    private final ResourceService resourceService;
    private final ResourceLifecycleService lifecycleService;
    private final DeploymentService deploymentService;

    public ResourcesController(
            ResourceService resourceService,
            ResourceLifecycleService lifecycleService,
            DeploymentService deploymentService) {
        this.resourceService = resourceService;
        this.lifecycleService = lifecycleService;
        this.deploymentService = deploymentService;
    }

    @Override
    public ResponseEntity<ResourceCreatedResponse> createResource(CreateResourceRequest createResourceRequest) {
        return ResponseEntity.status(202).body(resourceService.create(createResourceRequest, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<Resource> getResource(String slug) {
        return ResponseEntity.ok(resourceService.getBySlug(slug, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<PagedResourceResponse> listResources(
            String environmentSlug, Integer page, Integer size, String sort) {
        return ResponseEntity.ok(resourceService.list(environmentSlug, page, size, sort, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<Resource> updateResource(String slug, UpdateResourceRequest updateResourceRequest) {
        return ResponseEntity.ok(resourceService.update(slug, updateResourceRequest, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<Void> deleteResource(String slug) {
        resourceService.delete(slug, AuthContextHolder.get());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> stopResource(String slug) {
        lifecycleService.stop(slug, AuthContextHolder.get());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> startResource(String slug) {
        lifecycleService.start(slug, AuthContextHolder.get());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> restartResource(String slug) {
        lifecycleService.restart(slug, AuthContextHolder.get());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<TriggerDeploymentResponse> triggerDeployment(
            String slug, TriggerDeploymentRequest triggerDeploymentRequest) {
        var result = deploymentService.trigger(slug, triggerDeploymentRequest, AuthContextHolder.get());
        return ResponseEntity.accepted().body(result);
    }

    @Override
    public ResponseEntity<Deployment> getDeployment(String slug, UUID deploymentId) {
        return ResponseEntity.ok(deploymentService.get(slug, deploymentId, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<PagedDeploymentResponse> listDeployments(
            String slug, Integer page, Integer size, String sort) {
        return ResponseEntity.ok(deploymentService.list(slug, page, size, sort, AuthContextHolder.get()));
    }
}
