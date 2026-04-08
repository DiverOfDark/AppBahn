package eu.appbahn.platform.workspace.controller;

import eu.appbahn.platform.api.WorkspacesApi;
import eu.appbahn.platform.api.model.AddMemberRequest;
import eu.appbahn.platform.api.model.AddMemberResponse;
import eu.appbahn.platform.api.model.CreateGroupMappingRequest;
import eu.appbahn.platform.api.model.CreateNotificationWebhookRequest;
import eu.appbahn.platform.api.model.CreateWorkspaceRequest;
import eu.appbahn.platform.api.model.NotificationWebhook;
import eu.appbahn.platform.api.model.OidcGroupMapping;
import eu.appbahn.platform.api.model.PagedAuditLogResponse;
import eu.appbahn.platform.api.model.PagedWorkspaceResponse;
import eu.appbahn.platform.api.model.Quota;
import eu.appbahn.platform.api.model.RegistryConfig;
import eu.appbahn.platform.api.model.SecuritySettings;
import eu.appbahn.platform.api.model.UpdateGroupMappingRequest;
import eu.appbahn.platform.api.model.UpdateMemberRequest;
import eu.appbahn.platform.api.model.UpdateNotificationWebhookRequest;
import eu.appbahn.platform.api.model.UpdateWorkspaceRequest;
import eu.appbahn.platform.api.model.WebhookDelivery;
import eu.appbahn.platform.api.model.Workspace;
import eu.appbahn.platform.api.model.WorkspaceMember;
import eu.appbahn.platform.common.security.AuthContextHolder;
import eu.appbahn.platform.workspace.service.GroupMappingService;
import eu.appbahn.platform.workspace.service.MemberService;
import eu.appbahn.platform.workspace.service.WorkspaceService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WorkspacesController implements WorkspacesApi {

    private final WorkspaceService workspaceService;
    private final MemberService memberService;
    private final GroupMappingService groupMappingService;

    public WorkspacesController(
            WorkspaceService workspaceService, MemberService memberService, GroupMappingService groupMappingService) {
        this.workspaceService = workspaceService;
        this.memberService = memberService;
        this.groupMappingService = groupMappingService;
    }

    @Override
    public ResponseEntity<Workspace> createWorkspace(CreateWorkspaceRequest createWorkspaceRequest) {
        return ResponseEntity.status(201)
                .body(workspaceService.create(createWorkspaceRequest, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<PagedWorkspaceResponse> listWorkspaces(Integer page, Integer size, String sort) {
        return ResponseEntity.ok(workspaceService.list(page, size, sort, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<Workspace> getWorkspace(String slug) {
        return ResponseEntity.ok(workspaceService.getBySlug(slug, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<Workspace> updateWorkspace(String slug, UpdateWorkspaceRequest updateWorkspaceRequest) {
        return ResponseEntity.ok(workspaceService.update(slug, updateWorkspaceRequest, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<Void> deleteWorkspace(String slug) {
        workspaceService.delete(slug, AuthContextHolder.get());
        return ResponseEntity.noContent().build();
    }

    // --- Member management ---

    @Override
    public ResponseEntity<List<WorkspaceMember>> listWorkspaceMembers(String slug) {
        return ResponseEntity.ok(memberService.listMembers(slug, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<AddMemberResponse> addWorkspaceMember(String slug, AddMemberRequest addMemberRequest) {
        return ResponseEntity.status(201)
                .body(memberService.addMember(slug, addMemberRequest, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<WorkspaceMember> updateWorkspaceMember(
            String slug, UUID userId, UpdateMemberRequest updateMemberRequest) {
        return ResponseEntity.ok(
                memberService.updateMember(slug, userId, updateMemberRequest, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<Void> removeWorkspaceMember(String slug, UUID userId) {
        memberService.removeMember(slug, userId, AuthContextHolder.get());
        return ResponseEntity.noContent().build();
    }

    // --- Group mappings ---

    @Override
    public ResponseEntity<List<OidcGroupMapping>> listGroupMappings(String slug) {
        return ResponseEntity.ok(groupMappingService.list(slug, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<OidcGroupMapping> createGroupMapping(
            String slug, CreateGroupMappingRequest createGroupMappingRequest) {
        return ResponseEntity.status(201)
                .body(groupMappingService.create(slug, createGroupMappingRequest, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<OidcGroupMapping> updateGroupMapping(
            String slug, UUID mappingId, UpdateGroupMappingRequest updateGroupMappingRequest) {
        return ResponseEntity.ok(
                groupMappingService.update(slug, mappingId, updateGroupMappingRequest, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<Void> deleteGroupMapping(String slug, UUID mappingId) {
        groupMappingService.delete(slug, mappingId, AuthContextHolder.get());
        return ResponseEntity.noContent().build();
    }

    // --- Not implemented in Sprint 3 ---

    @Override
    public ResponseEntity<NotificationWebhook> createNotificationWebhook(
            String slug, CreateNotificationWebhookRequest createNotificationWebhookRequest) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Void> deleteNotificationWebhook(String slug, UUID hookId) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<PagedAuditLogResponse> getWorkspaceAuditLog(
            String slug, Integer page, Integer size, String action, String targetType) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Quota> getWorkspaceQuota(String slug) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<SecuritySettings> getWorkspaceSecurity(String slug) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<List<NotificationWebhook>> listNotificationWebhooks(String slug) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<List<WebhookDelivery>> listWebhookDeliveries(String slug, UUID hookId) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Quota> setWorkspaceQuota(String slug, Quota quota) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Workspace> setWorkspaceRegistry(String slug, RegistryConfig registryConfig) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<SecuritySettings> setWorkspaceSecurity(String slug, SecuritySettings securitySettings) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<NotificationWebhook> updateNotificationWebhook(
            String slug, UUID hookId, UpdateNotificationWebhookRequest updateNotificationWebhookRequest) {
        return ResponseEntity.status(501).build();
    }
}
