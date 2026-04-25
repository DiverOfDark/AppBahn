package eu.appbahn.platform.api.workspace;

import eu.appbahn.platform.api.NotificationWebhook;
import eu.appbahn.platform.api.OidcGroupMapping;
import eu.appbahn.platform.api.PagedAuditLogResponse;
import eu.appbahn.platform.api.Quota;
import eu.appbahn.platform.api.RegistryConfig;
import eu.appbahn.platform.api.SecuritySettings;
import eu.appbahn.platform.api.UpdateMemberRequest;
import eu.appbahn.platform.api.WebhookDelivery;
import eu.appbahn.platform.api.Workspace;
import eu.appbahn.platform.api.WorkspaceMember;
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
@Tag(name = "Workspaces")
public interface WorkspacesApi {
    /**
     * POST /workspaces/{slug}/members : Add workspace member
     *
     * @param slug  (required)
     * @param addMemberRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Conflict (status code 409)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/workspaces/{slug}/members",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<AddMemberResponse> addWorkspaceMember(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable AddMemberRequest addMemberRequest);
    /**
     * POST /workspaces/{slug}/group-mappings : Create OIDC group mapping
     *
     * @param slug  (required)
     * @param createGroupMappingRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Conflict (status code 409)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/workspaces/{slug}/group-mappings",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<OidcGroupMapping> createGroupMapping(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable CreateGroupMappingRequest createGroupMappingRequest);
    /**
     * POST /workspaces/{slug}/notification-webhooks : Create notification webhook
     *
     * @param slug  (required)
     * @param createNotificationWebhookRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/workspaces/{slug}/notification-webhooks",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<NotificationWebhook> createNotificationWebhook(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable
                    CreateNotificationWebhookRequest createNotificationWebhookRequest);
    /**
     * POST /workspaces : Create workspace
     *
     * @param createWorkspaceRequest  (required)
     * @return Workspace created (status code 201)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Conflict (status code 409)
     *         or Unprocessable entity (status code 422)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/workspaces",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Workspace> createWorkspace(@Valid @RequestBody CreateWorkspaceRequest createWorkspaceRequest);
    /**
     * DELETE /workspaces/{slug}/group-mappings/{mapping_id} : Delete OIDC group mapping
     *
     * @param slug  (required)
     * @param mappingId  (required)
     * @return Group mapping deleted (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.DELETE,
            value = "/workspaces/{slug}/group-mappings/{mapping_id}",
            produces = {"application/json"})
    ResponseEntity<Void> deleteGroupMapping(
            @PathVariable("slug") String slug, @PathVariable("mapping_id") UUID mappingId);
    /**
     * DELETE /workspaces/{slug}/notification-webhooks/{hook_id} : Delete notification webhook
     *
     * @param slug  (required)
     * @param hookId  (required)
     * @return Notification webhook deleted (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.DELETE,
            value = "/workspaces/{slug}/notification-webhooks/{hook_id}",
            produces = {"application/json"})
    ResponseEntity<Void> deleteNotificationWebhook(
            @PathVariable("slug") String slug, @PathVariable("hook_id") UUID hookId);
    /**
     * DELETE /workspaces/{slug} : Delete workspace
     *
     * @param slug  (required)
     * @return Workspace deleted (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Conflict (status code 409)
     */
    @RequestMapping(
            method = RequestMethod.DELETE,
            value = "/workspaces/{slug}",
            produces = {"application/json"})
    ResponseEntity<Void> deleteWorkspace(@PathVariable("slug") String slug);
    /**
     * GET /workspaces/{slug} : Get workspace details
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/workspaces/{slug}",
            produces = {"application/json"})
    ResponseEntity<Workspace> getWorkspace(@PathVariable("slug") String slug);
    /**
     * GET /workspaces/{slug}/audit-log : Query workspace audit log
     *
     * @param slug  (required)
     * @param page  (optional)
     * @param size  (optional)
     * @param action  (optional)
     * @param targetType  (optional)
     * @param actorId  (optional)
     * @param from  (optional)
     * @param to  (optional)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/workspaces/{slug}/audit-log",
            produces = {"application/json"})
    ResponseEntity<PagedAuditLogResponse> getWorkspaceAuditLog(
            @PathVariable("slug") String slug,
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
                    OffsetDateTime to);
    /**
     * GET /workspaces/{slug}/quota : Get workspace quota
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/workspaces/{slug}/quota",
            produces = {"application/json"})
    ResponseEntity<Quota> getWorkspaceQuota(@PathVariable("slug") String slug);
    /**
     * GET /workspaces/{slug}/security : Get workspace security settings
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/workspaces/{slug}/security",
            produces = {"application/json"})
    ResponseEntity<SecuritySettings> getWorkspaceSecurity(@PathVariable("slug") String slug);
    /**
     * GET /workspaces/{slug}/group-mappings : List OIDC group mappings
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/workspaces/{slug}/group-mappings",
            produces = {"application/json"})
    ResponseEntity<List<OidcGroupMapping>> listGroupMappings(@PathVariable("slug") String slug);
    /**
     * GET /workspaces/{slug}/notification-webhooks : List notification webhooks
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/workspaces/{slug}/notification-webhooks",
            produces = {"application/json"})
    ResponseEntity<List<NotificationWebhook>> listNotificationWebhooks(@PathVariable("slug") String slug);
    /**
     * GET /workspaces/{slug}/notification-webhooks/{hook_id}/deliveries : List webhook deliveries
     *
     * @param slug  (required)
     * @param hookId  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/workspaces/{slug}/notification-webhooks/{hook_id}/deliveries",
            produces = {"application/json"})
    ResponseEntity<List<WebhookDelivery>> listWebhookDeliveries(
            @PathVariable("slug") String slug, @PathVariable("hook_id") UUID hookId);
    /**
     * GET /workspaces/{slug}/members : List workspace members
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/workspaces/{slug}/members",
            produces = {"application/json"})
    ResponseEntity<List<WorkspaceMember>> listWorkspaceMembers(@PathVariable("slug") String slug);
    /**
     * GET /workspaces : List workspaces
     *
     * @param page  (optional)
     * @param size  (optional)
     * @param sort  (optional)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/workspaces",
            produces = {"application/json"})
    ResponseEntity<PagedWorkspaceResponse> listWorkspaces(
            @Valid @RequestParam(value = "page", required = false) @Nullable Integer page,
            @Valid @RequestParam(value = "size", required = false) @Nullable Integer size,
            @Valid @RequestParam(value = "sort", required = false) @Nullable String sort);
    /**
     * DELETE /workspaces/{slug}/members/{user_id} : Remove workspace member
     *
     * @param slug  (required)
     * @param userId  (required)
     * @return Member removed (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.DELETE,
            value = "/workspaces/{slug}/members/{user_id}",
            produces = {"application/json"})
    ResponseEntity<Void> removeWorkspaceMember(@PathVariable("slug") String slug, @PathVariable("user_id") UUID userId);
    /**
     * PATCH /workspaces/{slug}/quota : Set workspace quota
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
            value = "/workspaces/{slug}/quota",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Quota> setWorkspaceQuota(
            @PathVariable("slug") String slug, @Valid @RequestBody(required = false) @Nullable Quota quota);
    /**
     * PUT /workspaces/{slug}/registry : Set workspace registry configuration
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
            value = "/workspaces/{slug}/registry",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Workspace> setWorkspaceRegistry(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable RegistryConfig registryConfig);
    /**
     * PUT /workspaces/{slug}/security : Set workspace security settings
     *
     * @param slug  (required)
     * @param securitySettings  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.PUT,
            value = "/workspaces/{slug}/security",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<SecuritySettings> setWorkspaceSecurity(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable SecuritySettings securitySettings);
    /**
     * PATCH /workspaces/{slug}/group-mappings/{mapping_id} : Update OIDC group mapping
     *
     * @param slug  (required)
     * @param mappingId  (required)
     * @param updateGroupMappingRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.PATCH,
            value = "/workspaces/{slug}/group-mappings/{mapping_id}",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<OidcGroupMapping> updateGroupMapping(
            @PathVariable("slug") String slug,
            @PathVariable("mapping_id") UUID mappingId,
            @Valid @RequestBody(required = false) @Nullable UpdateGroupMappingRequest updateGroupMappingRequest);
    /**
     * PATCH /workspaces/{slug}/notification-webhooks/{hook_id} : Update notification webhook
     *
     * @param slug  (required)
     * @param hookId  (required)
     * @param updateNotificationWebhookRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.PATCH,
            value = "/workspaces/{slug}/notification-webhooks/{hook_id}",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<NotificationWebhook> updateNotificationWebhook(
            @PathVariable("slug") String slug,
            @PathVariable("hook_id") UUID hookId,
            @Valid @RequestBody(required = false) @Nullable
                    UpdateNotificationWebhookRequest updateNotificationWebhookRequest);
    /**
     * PATCH /workspaces/{slug} : Update workspace
     *
     * @param slug  (required)
     * @param updateWorkspaceRequest  (required)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Unprocessable entity (status code 422)
     */
    @RequestMapping(
            method = RequestMethod.PATCH,
            value = "/workspaces/{slug}",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Workspace> updateWorkspace(
            @PathVariable("slug") String slug, @Valid @RequestBody UpdateWorkspaceRequest updateWorkspaceRequest);
    /**
     * PATCH /workspaces/{slug}/members/{user_id} : Update workspace member role
     *
     * @param slug  (required)
     * @param userId  (required)
     * @param updateMemberRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.PATCH,
            value = "/workspaces/{slug}/members/{user_id}",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<WorkspaceMember> updateWorkspaceMember(
            @PathVariable("slug") String slug,
            @PathVariable("user_id") UUID userId,
            @Valid @RequestBody(required = false) @Nullable UpdateMemberRequest updateMemberRequest);
}
