package eu.appbahn.platform.api.resource;

import eu.appbahn.platform.api.Deployment;
import eu.appbahn.platform.api.DeploymentApproval;
import eu.appbahn.platform.api.DomainEntry;
import eu.appbahn.platform.api.ErrorResponse;
import eu.appbahn.platform.api.Resource;
import eu.appbahn.platform.api.ResourceExposure;
import eu.appbahn.platform.api.WebhookConfig;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
     * POST /resources/preview : PreviewResource — compute the slug + primary ingress domain
     * that {@code POST /resources} would mint for the same payload, without persisting anything.
     * Each call generates a fresh random suffix; the SPA uses the result for the create-form
     * Summary rail. Validates auth (EDITOR on the target environment) and the request shape,
     * but skips quota, license, and cluster-reachability checks.
     *
     * @param createResourceRequest  (required)
     * @return Preview computed (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found — environment does not exist (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/preview",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<ResourcePreviewResponse> previewResource(
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
     *         or Conflict — downstream ImageSource references the bound ImageSource of this
     *         Resource, or other Resources link to it. Body is an {@link ErrorResponse} whose
     *         {@code error} is {@code resource_has_downstream_image_sources} or
     *         {@code resource_has_dependents}, and {@code details} is the list of blocking
     *         Resource slugs. (status code 409)
     */
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Resource deleted"),
        @ApiResponse(
                responseCode = "409",
                description = "Cannot delete: dependent Resources or downstream ImageSources reference this Resource",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
     * @param container Filter by container name (optional)
     * @param pod Filter by pod name (optional)
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
            @Valid @RequestParam(value = "container", required = false) @Nullable String container,
            @Valid @RequestParam(value = "pod", required = false) @Nullable String pod,
            @Valid @RequestParam(value = "deploymentId", required = false) @Nullable UUID deploymentId,
            @Valid @RequestParam(value = "lines", required = false) @Nullable Integer lines,
            @Valid
                    @RequestParam(value = "since", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    @Nullable
                    OffsetDateTime since);
    /**
     * GET /resources/{slug}/logs/stream : GetResourceLogStream — open a Server-Sent Events
     * stream of live container logs and Kubernetes events for the Resource. Two event types are
     * emitted: {@code log} ({@link LogStreamLogFrame} — container output tailed from the log
     * provider) and {@code k8s_event} ({@link LogStreamEventFrame} — events from objects owned by
     * the Resource). The connection stays open indefinitely with periodic {@code keepalive}
     * frames. Filters: {@code container}, {@code pod}, {@code since} (lower bound for the initial
     * backfill), and {@code types} (comma-separated subset of {@code log,k8s_event}; default both).
     * When no log provider is configured the {@code log} channel is silently inactive and only
     * {@code k8s_event} frames flow.
     *
     * @param slug  (required)
     * @param container Filter by container name (optional)
     * @param pod Filter by pod name (optional)
     * @param since Lower time bound for the initial backfill (optional)
     * @param types Comma-separated event types to receive: log, k8s_event (optional, default both)
     * @return SSE stream (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}/logs/stream",
            produces = {"text/event-stream"})
    SseEmitter getResourceLogStream(
            @PathVariable("slug") String slug,
            @Valid @RequestParam(value = "container", required = false) @Nullable String container,
            @Valid @RequestParam(value = "pod", required = false) @Nullable String pod,
            @Valid
                    @RequestParam(value = "since", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    @Nullable
                    OffsetDateTime since,
            @Valid @RequestParam(value = "types", required = false) @Nullable String types);
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
     * GET /resources/{slug}/pods : GetResourcePods — per-pod table data for the Resource
     * Detail Overview's pod panel and the Scale modal's baseline. The operator queries
     * fabric8 live for the pod list and best-effort queries metrics-server for current
     * CPU/memory usage; usage fields are null when metrics-server isn't installed.
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Gateway timeout (status code 504) — operator did not answer within the budget
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}/pods",
            produces = {"application/json"})
    ResponseEntity<PodsResponse> getResourcePods(@PathVariable("slug") String slug);
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
     * @param lifecycle Lifecycle-bucket filter for the Deploys-tab tabs. Defaults to {@code All}
     *                  when omitted. (optional)
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
            @Valid @RequestParam(value = "lifecycle", required = false) @Nullable DeploymentLifecycleFilter lifecycle,
            @Valid @RequestParam(value = "page", required = false) @Nullable Integer page,
            @Valid @RequestParam(value = "size", required = false) @Nullable Integer size,
            @Valid @RequestParam(value = "sort", required = false) @Nullable String sort);

    /**
     * GET /resources/{slug}/deployments/stats : GetDeploymentStats — daily histogram + aggregate
     * counters for the Deploys tab. Aggregation runs server-side via SQL; window defaults to 30
     * days when omitted and is clamped to {@code [1, 90]}.
     *
     * @param slug  (required)
     * @param windowDays Window length in days. (optional)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/resources/{slug}/deployments/stats",
            produces = {"application/json"})
    ResponseEntity<DeploymentStats> getDeploymentStats(
            @PathVariable("slug") String slug,
            @Parameter(description = "Window length in days. Defaults to 30. Clamped to [1, 90].")
                    @Valid
                    @RequestParam(value = "windowDays", required = false)
                    @Nullable
                    Integer windowDays);

    /**
     * POST /resources/{slug}/deployments/{deployment_id}/cancel : CancelDeployment — abort an
     * in-flight deployment. Allowed only while the deployment lifecycle is {@code Queued} or
     * {@code Building}; from {@code Built} onward the rollout owns the row and cancellation is
     * rejected with HTTP 409.
     *
     * <p>Marks the audit row {@code Canceled} in-transaction and enqueues a tunnel command for
     * the operator to delete the in-flight build {@code Job}.
     *
     * @param slug  (required)
     * @param deploymentId  (required)
     * @return Deployment cancelled (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Conflict — the deployment is past the cancellable phase (Built / Activating /
     *         Active / Failed / Superseded / Canceled). Body is an
     *         {@link ErrorResponse}. (status code 409)
     */
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deployment cancelled"),
        @ApiResponse(
                responseCode = "409",
                description = "Cannot cancel: deployment is past the cancellable phase",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/{slug}/deployments/{deployment_id}/cancel",
            produces = {"application/json"})
    ResponseEntity<Void> cancelDeployment(
            @PathVariable("slug") String slug, @PathVariable("deployment_id") UUID deploymentId);

    /**
     * POST /resources/{slug}/deployments/{deployment_id}/retry : RetryDeployment — re-deploy
     * the same source (image / git commit) as the named deployment. Always creates a new
     * deployment audit row even when the source deployment is still active, so the audit trail
     * has one row per user-initiated action.
     *
     * @param slug  (required)
     * @param deploymentId  (required)
     * @return The new deployment row (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Unprocessable entity — the source deployment carries no imageRef or sourceRef
     *         to re-deploy. (status code 422)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/{slug}/deployments/{deployment_id}/retry",
            produces = {"application/json"})
    ResponseEntity<Deployment> retryDeployment(
            @PathVariable("slug") String slug, @PathVariable("deployment_id") UUID deploymentId);
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
     * POST /resources/{slug}/promote : PromoteResource — pin the bound ImageSource's
     * {@code pinnedDigest} to either the supplied digest or the upstream's current latest.
     *
     * @param slug  (required)
     * @param promoteRequest  (optional)
     * @return Resource promoted (status code 204)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Unprocessable entity (status code 422)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/{slug}/promote",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Void> promoteResource(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable PromoteRequest promoteRequest);
    /**
     * POST /resources/{slug}/rollback : RollbackResource — pin {@code Resource.spec.pinnedRelease}
     * to a previous deployment's snapshot. Works for any ImageSource type ({@code git},
     * {@code image}, {@code imageSource}) — the pin lives on the Resource, not the ImageSource,
     * so no rebuild runs.
     *
     * @param slug  (required)
     * @param rollbackRequest  (optional)
     * @return Resource rollback requested (status code 204)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Unprocessable entity (status code 422)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/{slug}/rollback",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Void> rollbackResource(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable RollbackRequest rollbackRequest);
    /**
     * POST /resources/{slug}/unpin : UnpinResource — clear {@code Resource.spec.pinnedRelease}.
     * The Resource immediately resumes following the bound ImageSource's
     * {@code latestArtifact} (which may be newer than the pin if builds ran while pinned).
     *
     * @param slug  (required)
     * @return Resource unpin requested (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/resources/{slug}/unpin",
            produces = {"application/json"})
    ResponseEntity<Void> unpinResource(@PathVariable("slug") String slug);
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
