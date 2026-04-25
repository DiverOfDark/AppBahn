package eu.appbahn.platform.api.environment;

import eu.appbahn.platform.api.ApprovalGatesConfig;
import eu.appbahn.platform.api.Environment;
import eu.appbahn.platform.api.EnvironmentToken;
import eu.appbahn.platform.api.Quota;
import eu.appbahn.platform.api.RegistryConfig;
import eu.appbahn.platform.api.UpdateMemberRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@Tag(name = "Environments")
public interface EnvironmentsApi {
    /**
     * POST /environments : Create environment
     *
     * @param createEnvironmentRequest  (required)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Conflict (status code 409)
     *         or Unprocessable entity (status code 422)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/environments",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Environment> createEnvironment(
            @Valid @RequestBody CreateEnvironmentRequest createEnvironmentRequest);
    /**
     * POST /environments/{slug}/tokens : Create environment token
     *
     * @param slug  (required)
     * @param createEnvironmentTokenRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Unprocessable entity (status code 422)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/environments/{slug}/tokens",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<CreateEnvironmentTokenResponse> createEnvironmentToken(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable
                    CreateEnvironmentTokenRequest createEnvironmentTokenRequest);
    /**
     * DELETE /environments/{slug} : Delete environment
     *
     * @param slug  (required)
     * @return Environment deleted (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.DELETE,
            value = "/environments/{slug}",
            produces = {"application/json"})
    ResponseEntity<Void> deleteEnvironment(@PathVariable("slug") String slug);
    /**
     * DELETE /environments/{slug}/members/{user_id}/role : Remove environment-level role override
     *
     * @param slug  (required)
     * @param userId  (required)
     * @return Role override removed (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.DELETE,
            value = "/environments/{slug}/members/{user_id}/role",
            produces = {"application/json"})
    ResponseEntity<Void> deleteEnvironmentMemberRole(
            @PathVariable("slug") String slug, @PathVariable("user_id") UUID userId);
    /**
     * DELETE /environments/{slug}/tokens/{token_id} : Delete environment token
     *
     * @param slug  (required)
     * @param tokenId  (required)
     * @return Token deleted (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.DELETE,
            value = "/environments/{slug}/tokens/{token_id}",
            produces = {"application/json"})
    ResponseEntity<Void> deleteEnvironmentToken(
            @PathVariable("slug") String slug, @PathVariable("token_id") UUID tokenId);
    /**
     * GET /environments/{slug} : Get environment details
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/environments/{slug}",
            produces = {"application/json"})
    ResponseEntity<Environment> getEnvironment(@PathVariable("slug") String slug);
    /**
     * GET /environments/{slug}/quota : Get environment quota
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/environments/{slug}/quota",
            produces = {"application/json"})
    ResponseEntity<Quota> getEnvironmentQuota(@PathVariable("slug") String slug);
    /**
     * GET /environments/{slug}/tokens : List environment tokens
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/environments/{slug}/tokens",
            produces = {"application/json"})
    ResponseEntity<List<EnvironmentToken>> listEnvironmentTokens(@PathVariable("slug") String slug);
    /**
     * GET /environments : List environments
     *
     * @param projectSlug  (optional)
     * @param page  (optional)
     * @param size  (optional)
     * @param sort  (optional)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/environments",
            produces = {"application/json"})
    ResponseEntity<PagedEnvironmentResponse> listEnvironments(
            @Valid @RequestParam(value = "projectSlug", required = false) @Nullable String projectSlug,
            @Valid @RequestParam(value = "page", required = false) @Nullable Integer page,
            @Valid @RequestParam(value = "size", required = false) @Nullable Integer size,
            @Valid @RequestParam(value = "sort", required = false) @Nullable String sort);
    /**
     * PUT /environments/{slug}/approval-gates : Set deployment approval gates
     *
     * @param slug  (required)
     * @param approvalGatesConfig  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.PUT,
            value = "/environments/{slug}/approval-gates",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Environment> setApprovalGates(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable ApprovalGatesConfig approvalGatesConfig);
    /**
     * PUT /environments/{slug}/members/{user_id}/role : Set environment-level role override
     *
     * @param slug  (required)
     * @param userId  (required)
     * @param updateMemberRequest  (optional)
     * @return Role override set (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Unprocessable entity (status code 422)
     */
    @RequestMapping(
            method = RequestMethod.PUT,
            value = "/environments/{slug}/members/{user_id}/role",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Void> setEnvironmentMemberRole(
            @PathVariable("slug") String slug,
            @PathVariable("user_id") UUID userId,
            @Valid @RequestBody(required = false) @Nullable UpdateMemberRequest updateMemberRequest);
    /**
     * PATCH /environments/{slug}/quota : Set environment quota
     *
     * @param slug  (required)
     * @param quota  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.PATCH,
            value = "/environments/{slug}/quota",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Quota> setEnvironmentQuota(
            @PathVariable("slug") String slug, @Valid @RequestBody(required = false) @Nullable Quota quota);
    /**
     * PUT /environments/{slug}/registry : Set environment registry configuration
     *
     * @param slug  (required)
     * @param registryConfig  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.PUT,
            value = "/environments/{slug}/registry",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Environment> setEnvironmentRegistry(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable RegistryConfig registryConfig);
    /**
     * PUT /environments/{slug}/target-cluster : Set target cluster for environment
     *
     * @param slug  (required)
     * @param setTargetClusterRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.PUT,
            value = "/environments/{slug}/target-cluster",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Environment> setTargetCluster(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable SetTargetClusterRequest setTargetClusterRequest);
    /**
     * PATCH /environments/{slug} : Update environment
     *
     * @param slug  (required)
     * @param updateEnvironmentRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Unprocessable entity (status code 422)
     */
    @RequestMapping(
            method = RequestMethod.PATCH,
            value = "/environments/{slug}",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Environment> updateEnvironment(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable UpdateEnvironmentRequest updateEnvironmentRequest);
}
