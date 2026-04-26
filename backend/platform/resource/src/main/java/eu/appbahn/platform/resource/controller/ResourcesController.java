package eu.appbahn.platform.resource.controller;

import eu.appbahn.platform.api.Deployment;
import eu.appbahn.platform.api.DeploymentApproval;
import eu.appbahn.platform.api.DomainEntry;
import eu.appbahn.platform.api.Resource;
import eu.appbahn.platform.api.ResourceExposure;
import eu.appbahn.platform.api.WebhookConfig;
import eu.appbahn.platform.api.resource.AddDomainRequest;
import eu.appbahn.platform.api.resource.ConnectionResponse;
import eu.appbahn.platform.api.resource.CreateExposureRequest;
import eu.appbahn.platform.api.resource.CreateResourceRequest;
import eu.appbahn.platform.api.resource.LogResponse;
import eu.appbahn.platform.api.resource.MetricsResponse;
import eu.appbahn.platform.api.resource.PagedDeploymentResponse;
import eu.appbahn.platform.api.resource.PagedResourceResponse;
import eu.appbahn.platform.api.resource.ResourceCreatedResponse;
import eu.appbahn.platform.api.resource.ResourcesApi;
import eu.appbahn.platform.api.resource.RollbackRequest;
import eu.appbahn.platform.api.resource.TriggerDeploymentRequest;
import eu.appbahn.platform.api.resource.TriggerDeploymentResponse;
import eu.appbahn.platform.api.resource.UpdateResourceRequest;
import eu.appbahn.platform.common.exception.NotImplementedException;
import eu.appbahn.platform.common.security.AuthContextHolder;
import eu.appbahn.platform.resource.service.DeploymentService;
import eu.appbahn.platform.resource.service.ResourceLifecycleService;
import eu.appbahn.platform.resource.service.ResourceService;
import java.time.OffsetDateTime;
import java.util.List;
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

    @Override
    public ResponseEntity<DomainEntry> addDomain(String slug, AddDomainRequest addDomainRequest) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<Void> approveDeployment(String slug, UUID deploymentId) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<Void> clearBuildCache(String slug) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<ResourceExposure> createExposure(String slug, CreateExposureRequest createExposureRequest) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<Void> deleteExposure(String slug, Integer port) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<List<DeploymentApproval>> getDeploymentApprovals(String slug, UUID deploymentId) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<ConnectionResponse> getResourceConnection(String slug) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<MetricsResponse> getResourceCpuMetrics(
            String slug, String start, String end, Integer step, String pod) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<LogResponse> getResourceLogs(
            String slug, UUID deploymentId, Integer lines, OffsetDateTime since) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<MetricsResponse> getResourceNetworkInbound(
            String slug, String start, String end, Integer step, String pod) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<MetricsResponse> getResourceNetworkOutbound(
            String slug, String start, String end, Integer step, String pod) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<MetricsResponse> getResourceRamMetrics(
            String slug, String start, String end, Integer step, String pod) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<WebhookConfig> getResourceWebhook(String slug) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<List<DomainEntry>> listDomains(String slug) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<List<ResourceExposure>> listExposures(String slug) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<Void> rejectDeployment(String slug, UUID deploymentId) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<Void> removeDomain(String slug, String domain) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<Deployment> rollbackResource(String slug, RollbackRequest rollbackRequest) {
        throw new NotImplementedException();
    }

    @Override
    public ResponseEntity<WebhookConfig> rotateWebhookSecret(String slug) {
        throw new NotImplementedException();
    }
}
