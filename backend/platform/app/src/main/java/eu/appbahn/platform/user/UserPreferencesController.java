package eu.appbahn.platform.user;

import eu.appbahn.platform.api.AuditAction;
import eu.appbahn.platform.api.AuditTargetType;
import eu.appbahn.platform.api.user.UpdateUserPreferencesRequest;
import eu.appbahn.platform.api.user.UserPreferences;
import eu.appbahn.platform.api.user.UserPreferencesApi;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.exception.ValidationException;
import eu.appbahn.platform.common.security.AuthContextHolder;
import eu.appbahn.platform.user.entity.UserPreferencesEntity;
import eu.appbahn.platform.user.service.UserPreferencesService;
import eu.appbahn.platform.workspace.service.WorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class UserPreferencesController implements UserPreferencesApi {

    private final UserPreferencesService preferencesService;
    private final WorkspaceService workspaceService;
    private final AuditLogService auditLogService;

    public UserPreferencesController(
            UserPreferencesService preferencesService,
            WorkspaceService workspaceService,
            AuditLogService auditLogService) {
        this.preferencesService = preferencesService;
        this.workspaceService = workspaceService;
        this.auditLogService = auditLogService;
    }

    @Override
    public ResponseEntity<UserPreferences> getUserPreferences() {
        var ctx = AuthContextHolder.get();
        var entity = preferencesService.getOrEmpty(ctx.userId());
        return ResponseEntity.ok(toDto(entity));
    }

    @Override
    public ResponseEntity<UserPreferences> updateUserPreferences(UpdateUserPreferencesRequest request) {
        var ctx = AuthContextHolder.get();
        String slug = request.getDefaultWorkspaceSlug();

        if (slug != null) {
            try {
                // getWorkspaceId validates membership (throws ForbiddenException if not a member,
                // NotFoundException if the workspace doesn't exist)
                workspaceService.getWorkspaceId(slug, ctx);
            } catch (NotFoundException e) {
                throw new ValidationException("Workspace not found: " + slug);
            }
            // ForbiddenException propagates as-is (403) when the workspace exists but user has no role
        }

        var entity = preferencesService.upsert(ctx.userId(), slug);

        auditLogService
                .audit(ctx, AuditAction.USER_PREFERENCES_UPDATED)
                .target(AuditTargetType.USER, ctx.userId().toString())
                .save();

        return ResponseEntity.ok(toDto(entity));
    }

    private UserPreferences toDto(UserPreferencesEntity entity) {
        var dto = new UserPreferences();
        dto.setDefaultWorkspaceSlug(entity.getDefaultWorkspaceSlug());
        return dto;
    }
}
