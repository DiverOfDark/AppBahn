package eu.appbahn.platform.api.resource;

import eu.appbahn.platform.api.Deployment;
import eu.appbahn.platform.api.DeploymentApproval;
import eu.appbahn.platform.api.DomainEntry;
import eu.appbahn.platform.api.Resource;
import eu.appbahn.platform.api.ResourceExposure;
import eu.appbahn.platform.api.WebhookConfig;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@Tag(name = "Resources")
public interface ResourcesApi {
    /**
     * POST /resources/{slug}/domains : AddDomain
     *
     * @param slug  (required)
     * @param addDomainRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/{slug}/domains",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<DomainEntry> addDomain(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable AddDomainRequest addDomainRequest);
    /**
     * POST /resources/{slug}/deployments/{deployment_id}/approve : ApproveDeployment
     *
     * @param slug  (required)
     * @param deploymentId  (required)
     * @return Deployment approved (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Conflict (status code 409)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/{slug}/deployments/{deployment_id}/approve",
            produces = {"application/json"})
    ResponseEntity<Void> approveDeployment(
            @PathVariable("slug") String slug, @PathVariable("deployment_id") UUID deploymentId);
    /**
     * DELETE /resources/{slug}/build-cache : ClearBuildCache
     *
     * @param slug  (required)
     * @return Build cache cleared (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.DELETE,
            value = "/resources/{slug}/build-cache",
            produces = {"application/json"})
    ResponseEntity<Void> clearBuildCache(@PathVariable("slug") String slug);
    /**
     * POST /resources/{slug}/expose : CreateExposure
     *
     * @param slug  (required)
     * @param createExposureRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/{slug}/expose",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<ResourceExposure> createExposure(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable CreateExposureRequest createExposureRequest);
    /**
     * POST /resources : CreateResource
     *
     * @param createResourceRequest  (required)
     * @return Resource creation accepted (status code 202)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Resource limit reached (status code 402)
     *         or Forbidden (status code 403)
     *         or Conflict (status code 409)
     *         or Unprocessable entity (status code 422)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<ResourceCreatedResponse> createResource(
            @Valid @RequestBody CreateResourceRequest createResourceRequest);
    /**
     * DELETE /resources/{slug}/exposures/{port} : DeleteExposure
     *
     * @param slug  (required)
     * @param port  (required)
     * @return Exposure deleted (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.DELETE,
            value = "/resources/{slug}/exposures/{port}",
            produces = {"application/json"})
    ResponseEntity<Void> deleteExposure(@PathVariable("slug") String slug, @PathVariable("port") Integer port);
    /**
     * DELETE /resources/{slug} : DeleteResource
     *
     * @param slug  (required)
     * @return Resource deleted (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.DELETE,
            value = "/resources/{slug}",
            produces = {"application/json"})
    ResponseEntity<Void> deleteResource(@PathVariable("slug") String slug);
    /**
     * GET /resources/{slug}/deployments/{deployment_id} : GetDeployment
     *
     * @param slug  (required)
     * @param deploymentId  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}/deployments/{deployment_id}",
            produces = {"application/json"})
    ResponseEntity<Deployment> getDeployment(
            @PathVariable("slug") String slug, @PathVariable("deployment_id") UUID deploymentId);
    /**
     * GET /resources/{slug}/deployments/{deployment_id}/approvals : GetDeploymentApprovals
     *
     * @param slug  (required)
     * @param deploymentId  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}/deployments/{deployment_id}/approvals",
            produces = {"application/json"})
    ResponseEntity<List<DeploymentApproval>> getDeploymentApprovals(
            @PathVariable("slug") String slug, @PathVariable("deployment_id") UUID deploymentId);
    /**
     * GET /resources/{slug} : GetResource
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}",
            produces = {"application/json"})
    ResponseEntity<Resource> getResource(@PathVariable("slug") String slug);
    /**
     * GET /resources/{slug}/connection : GetResourceConnection
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}/connection",
            produces = {"application/json"})
    ResponseEntity<ConnectionResponse> getResourceConnection(@PathVariable("slug") String slug);
    /**
     * GET /resources/{slug}/metrics/cpu : GetResourceCpuMetrics
     *
     * @param slug  (required)
     * @param start ISO 8601 or relative (e.g. -1h, -24h, -7d) (optional)
     * @param end ISO 8601 or &#39;now&#39; (optional)
     * @param step Resolution in seconds (optional)
     * @param pod Filter by pod name (optional)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}/metrics/cpu",
            produces = {"application/json"})
    ResponseEntity<MetricsResponse> getResourceCpuMetrics(
            @PathVariable("slug") String slug,
            @Valid @RequestParam(value = "start", required = false) @Nullable String start,
            @Valid @RequestParam(value = "end", required = false) @Nullable String end,
            @Valid @RequestParam(value = "step", required = false) @Nullable Integer step,
            @Valid @RequestParam(value = "pod", required = false) @Nullable String pod);
    /**
     * GET /resources/{slug}/logs : GetResourceLogs
     *
     * @param slug  (required)
     * @param deploymentId  (optional)
     * @param lines  (optional)
     * @param since  (optional)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}/logs",
            produces = {"application/json"})
    ResponseEntity<LogResponse> getResourceLogs(
            @PathVariable("slug") String slug,
            @Valid @RequestParam(value = "deploymentId", required = false) @Nullable UUID deploymentId,
            @Valid @RequestParam(value = "lines", required = false) @Nullable Integer lines,
            @Valid
                    @RequestParam(value = "since", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    @Nullable
                    OffsetDateTime since);
    /**
     * GET /resources/{slug}/metrics/network/inbound : GetResourceNetworkInbound
     *
     * @param slug  (required)
     * @param start ISO 8601 or relative (e.g. -1h, -24h, -7d) (optional)
     * @param end ISO 8601 or &#39;now&#39; (optional)
     * @param step Resolution in seconds (optional)
     * @param pod Filter by pod name (optional)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}/metrics/network/inbound",
            produces = {"application/json"})
    ResponseEntity<MetricsResponse> getResourceNetworkInbound(
            @PathVariable("slug") String slug,
            @Valid @RequestParam(value = "start", required = false) @Nullable String start,
            @Valid @RequestParam(value = "end", required = false) @Nullable String end,
            @Valid @RequestParam(value = "step", required = false) @Nullable Integer step,
            @Valid @RequestParam(value = "pod", required = false) @Nullable String pod);
    /**
     * GET /resources/{slug}/metrics/network/outbound : GetResourceNetworkOutbound
     *
     * @param slug  (required)
     * @param start ISO 8601 or relative (e.g. -1h, -24h, -7d) (optional)
     * @param end ISO 8601 or &#39;now&#39; (optional)
     * @param step Resolution in seconds (optional)
     * @param pod Filter by pod name (optional)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}/metrics/network/outbound",
            produces = {"application/json"})
    ResponseEntity<MetricsResponse> getResourceNetworkOutbound(
            @PathVariable("slug") String slug,
            @Valid @RequestParam(value = "start", required = false) @Nullable String start,
            @Valid @RequestParam(value = "end", required = false) @Nullable String end,
            @Valid @RequestParam(value = "step", required = false) @Nullable Integer step,
            @Valid @RequestParam(value = "pod", required = false) @Nullable String pod);
    /**
     * GET /resources/{slug}/metrics/ram : GetResourceRamMetrics
     *
     * @param slug  (required)
     * @param start ISO 8601 or relative (e.g. -1h, -24h, -7d) (optional)
     * @param end ISO 8601 or &#39;now&#39; (optional)
     * @param step Resolution in seconds (optional)
     * @param pod Filter by pod name (optional)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}/metrics/ram",
            produces = {"application/json"})
    ResponseEntity<MetricsResponse> getResourceRamMetrics(
            @PathVariable("slug") String slug,
            @Valid @RequestParam(value = "start", required = false) @Nullable String start,
            @Valid @RequestParam(value = "end", required = false) @Nullable String end,
            @Valid @RequestParam(value = "step", required = false) @Nullable Integer step,
            @Valid @RequestParam(value = "pod", required = false) @Nullable String pod);
    /**
     * GET /resources/{slug}/webhook : GetResourceWebhook
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}/webhook",
            produces = {"application/json"})
    ResponseEntity<WebhookConfig> getResourceWebhook(@PathVariable("slug") String slug);
    /**
     * GET /resources/{slug}/deployments : ListDeployments
     *
     * @param slug  (required)
     * @param page  (optional)
     * @param size  (optional)
     * @param sort Sort field and direction (e.g. createdAt,desc). Defaults to createdAt,desc. (optional)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}/deployments",
            produces = {"application/json"})
    ResponseEntity<PagedDeploymentResponse> listDeployments(
            @PathVariable("slug") String slug,
            @Valid @RequestParam(value = "page", required = false) @Nullable Integer page,
            @Valid @RequestParam(value = "size", required = false) @Nullable Integer size,
            @Valid @RequestParam(value = "sort", required = false) @Nullable String sort);
    /**
     * GET /resources/{slug}/domains : ListDomains
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}/domains",
            produces = {"application/json"})
    ResponseEntity<List<DomainEntry>> listDomains(@PathVariable("slug") String slug);
    /**
     * GET /resources/{slug}/exposures : ListExposures
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}/exposures",
            produces = {"application/json"})
    ResponseEntity<List<ResourceExposure>> listExposures(@PathVariable("slug") String slug);
    /**
     * GET /resources : ListResources
     *
     * @param environmentSlug  (optional)
     * @param page  (optional)
     * @param size  (optional)
     * @param sort  (optional)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources",
            produces = {"application/json"})
    ResponseEntity<PagedResourceResponse> listResources(
            @Valid @RequestParam(value = "environmentSlug", required = false) @Nullable String environmentSlug,
            @Valid @RequestParam(value = "page", required = false) @Nullable Integer page,
            @Valid @RequestParam(value = "size", required = false) @Nullable Integer size,
            @Valid @RequestParam(value = "sort", required = false) @Nullable String sort);
    /**
     * POST /resources/{slug}/deployments/{deployment_id}/reject : RejectDeployment
     *
     * @param slug  (required)
     * @param deploymentId  (required)
     * @return Deployment rejected (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Conflict (status code 409)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/{slug}/deployments/{deployment_id}/reject",
            produces = {"application/json"})
    ResponseEntity<Void> rejectDeployment(
            @PathVariable("slug") String slug, @PathVariable("deployment_id") UUID deploymentId);
    /**
     * DELETE /resources/{slug}/domains/{domain} : RemoveDomain
     *
     * @param slug  (required)
     * @param domain  (required)
     * @return Domain removed (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.DELETE,
            value = "/resources/{slug}/domains/{domain}",
            produces = {"application/json"})
    ResponseEntity<Void> removeDomain(@PathVariable("slug") String slug, @PathVariable("domain") String domain);
    /**
     * POST /resources/{slug}/restart : RestartResource
     *
     * @param slug  (required)
     * @return Resource restarted (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/{slug}/restart",
            produces = {"application/json"})
    ResponseEntity<Void> restartResource(@PathVariable("slug") String slug);
    /**
     * POST /resources/{slug}/rollback : RollbackResource
     *
     * @param slug  (required)
     * @param rollbackRequest  (optional)
     * @return Rollback deployment created (status code 201)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/{slug}/rollback",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Deployment> rollbackResource(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable RollbackRequest rollbackRequest);
    /**
     * POST /resources/{slug}/webhook/rotate : RotateWebhookSecret
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/{slug}/webhook/rotate",
            produces = {"application/json"})
    ResponseEntity<WebhookConfig> rotateWebhookSecret(@PathVariable("slug") String slug);
    /**
     * POST /resources/{slug}/start : StartResource
     *
     * @param slug  (required)
     * @return Resource started (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/{slug}/start",
            produces = {"application/json"})
    ResponseEntity<Void> startResource(@PathVariable("slug") String slug);
    /**
     * POST /resources/{slug}/stop : StopResource
     *
     * @param slug  (required)
     * @return Resource stopped (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/{slug}/stop",
            produces = {"application/json"})
    ResponseEntity<Void> stopResource(@PathVariable("slug") String slug);
    /**
     * POST /resources/{slug}/deployments : TriggerDeployment
     *
     * @param slug  (required)
     * @param triggerDeploymentRequest  (optional)
     * @return Deployment accepted (status code 202)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/{slug}/deployments",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<TriggerDeploymentResponse> triggerDeployment(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable TriggerDeploymentRequest triggerDeploymentRequest);
    /**
     * PATCH /resources/{slug} : UpdateResource
     *
     * @param slug  (required)
     * @param updateResourceRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Conflict (status code 409)
     *         or Unprocessable entity (status code 422)
     */
    @RequestMapping(
            method = RequestMethod.PATCH,
            value = "/resources/{slug}",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Resource> updateResource(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable UpdateResourceRequest updateResourceRequest);
}
