package eu.appbahn.platform.api.admin;

import eu.appbahn.platform.api.Cluster;
import eu.appbahn.platform.api.NetworkPolicy;
import eu.appbahn.platform.api.PagedAuditLogResponse;
import eu.appbahn.platform.api.PlatformConfig;
import eu.appbahn.platform.api.ResourceTypeDefinition;
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
@Tag(name = "Admin")
public interface AdminApi {
    /**
     * POST /admin/network-policies : CreateNetworkPolicy
     *
     * @param createNetworkPolicyRequest  (required)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/admin/network-policies",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<NetworkPolicy> createNetworkPolicy(
            @Valid @RequestBody CreateNetworkPolicyRequest createNetworkPolicyRequest);
    /**
     * DELETE /admin/clusters/{name} : DeleteCluster
     *
     * @param name  (required)
     * @return Cluster deleted (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.DELETE,
            value = "/admin/clusters/{name}",
            produces = {"application/json"})
    ResponseEntity<Void> deleteCluster(@PathVariable("name") String name);
    /**
     * DELETE /admin/network-policies/{id} : DeleteNetworkPolicy
     *
     * @param id  (required)
     * @return Network policy deleted (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.DELETE,
            value = "/admin/network-policies/{id}",
            produces = {"application/json"})
    ResponseEntity<Void> deleteNetworkPolicy(@PathVariable("id") UUID id);
    /**
     * GET /admin/audit-log : GetPlatformAuditLog
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param action  (optional)
     * @param targetType  (optional)
     * @param actorId  (optional)
     * @param from  (optional)
     * @param to  (optional)
     * @param workspaceSlug  (optional)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/admin/audit-log",
            produces = {"application/json"})
    ResponseEntity<PagedAuditLogResponse> getPlatformAuditLog(
            @Valid @RequestParam(value = "page", required = false) @Nullable Integer page,
            @Valid @RequestParam(value = "size", required = false) @Nullable Integer size,
            @Valid @RequestParam(value = "action", required = false) @Nullable String action,
            @Valid @RequestParam(value = "targetType", required = false) @Nullable String targetType,
            @Valid @RequestParam(value = "actorId", required = false) @Nullable UUID actorId,
            @Valid
                    @RequestParam(value = "from", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    @Nullable
                    OffsetDateTime from,
            @Valid
                    @RequestParam(value = "to", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    @Nullable
                    OffsetDateTime to,
            @Valid @RequestParam(value = "workspaceSlug", required = false) @Nullable String workspaceSlug);
    /**
     * GET /admin/config : GetPlatformConfig
     *
     * @return Success (status code 200)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/admin/config",
            produces = {"application/json"})
    ResponseEntity<PlatformConfig> getPlatformConfig();
    /**
     * GET /admin/resource-types/{type} : GetResourceTypeDefinition
     *
     * @param type  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/admin/resource-types/{type}",
            produces = {"application/json"})
    ResponseEntity<ResourceTypeDefinition> getResourceTypeDefinition(@PathVariable("type") String type);
    /**
     * GET /admin/clusters : ListClusters
     *
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/admin/clusters",
            produces = {"application/json"})
    ResponseEntity<List<Cluster>> listClusters();
    /**
     * GET /admin/network-policies : ListNetworkPolicies
     *
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/admin/network-policies",
            produces = {"application/json"})
    ResponseEntity<List<NetworkPolicy>> listNetworkPolicies();
    /**
     * GET /admin/resource-types : ListResourceTypeDefinitions
     *
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/admin/resource-types",
            produces = {"application/json"})
    ResponseEntity<List<ResourceTypeDefinition>> listResourceTypeDefinitions();
    /**
     * GET /admin/users : ListUsers
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param email  (optional)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/admin/users",
            produces = {"application/json"})
    ResponseEntity<PagedUserResponse> listUsers(
            @Valid @RequestParam(value = "page", required = false) @Nullable Integer page,
            @Valid @RequestParam(value = "size", required = false) @Nullable Integer size,
            @Valid @RequestParam(value = "email", required = false) @Nullable String email);
    /**
     * POST /admin/clusters : RegisterCluster
     *
     * @param createClusterRequest  (required)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Conflict (status code 409)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/admin/clusters",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Cluster> registerCluster(@Valid @RequestBody CreateClusterRequest createClusterRequest);
    /**
     * PUT /admin/config : SetPlatformConfig
     *
     * @param platformConfig  (required)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     */
    @RequestMapping(
            method = RequestMethod.PUT,
            value = "/admin/config",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<PlatformConfig> setPlatformConfig(@Valid @RequestBody PlatformConfig platformConfig);
    /**
     * PATCH /admin/clusters/{name} : UpdateCluster
     *
     * @param name  (required)
     * @param updateClusterRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.PATCH,
            value = "/admin/clusters/{name}",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Cluster> updateCluster(
            @PathVariable("name") String name,
            @Valid @RequestBody(required = false) @Nullable UpdateClusterRequest updateClusterRequest);
    /**
     * PATCH /admin/network-policies/{id} : UpdateNetworkPolicy
     *
     * @param id  (required)
     * @param updateNetworkPolicyRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.PATCH,
            value = "/admin/network-policies/{id}",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<NetworkPolicy> updateNetworkPolicy(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) @Nullable UpdateNetworkPolicyRequest updateNetworkPolicyRequest);
    /**
     * PATCH /admin/resource-types/{type}/admin : UpdateResourceTypeAdminConfig
     *
     * @param type  (required)
     * @param updateResourceTypeAdminConfigRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.PATCH,
            value = "/admin/resource-types/{type}/admin",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<ResourceTypeDefinition> updateResourceTypeAdminConfig(
            @PathVariable("type") String type,
            @Valid @RequestBody(required = false) @Nullable
                    UpdateResourceTypeAdminConfigRequest updateResourceTypeAdminConfigRequest);
}
