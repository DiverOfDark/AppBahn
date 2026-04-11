package eu.appbahn.platform.user.controller;

import eu.appbahn.platform.api.AdminApi;
import eu.appbahn.platform.api.model.PagedAuditLogResponse;
import eu.appbahn.platform.api.model.PlatformConfig;
import eu.appbahn.platform.api.model.PlatformConfigBranding;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.ForbiddenException;
import eu.appbahn.platform.common.security.AuthContextHolder;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AdminController implements AdminApi {

    @Value("${platform.branding.instance-name:AppBahn}")
    private String instanceName;

    @Value("${platform.branding.tagline:Deploy and manage your applications}")
    private String tagline;

    @Value("${platform.branding.logo-url:}")
    private String logoUrl;

    @Value("${platform.branding.login-button-text:Log in with SSO}")
    private String loginButtonText;

    @Value("${platform.namespace-prefix:abp}")
    private String namespacePrefix;

    private final AuditLogService auditLogService;

    public AdminController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public ResponseEntity<PlatformConfig> getPlatformConfig() {
        PlatformConfigBranding branding = new PlatformConfigBranding();
        branding.setInstanceName(instanceName);
        branding.setTagline(tagline);
        branding.setLogoUrl(logoUrl);
        branding.setLoginButtonText(loginButtonText);

        PlatformConfig config = new PlatformConfig();
        config.setBranding(branding);
        config.setNamespacePrefix(namespacePrefix);
        return ResponseEntity.ok(config);
    }

    @Override
    public ResponseEntity<PagedAuditLogResponse> getPlatformAuditLog(
            Integer page,
            Integer size,
            String action,
            String targetType,
            UUID actorId,
            OffsetDateTime from,
            OffsetDateTime to,
            String workspaceSlug) {
        var ctx = AuthContextHolder.get();
        if (!ctx.platformAdmin()) {
            throw new ForbiddenException("Platform admin access required");
        }
        return ResponseEntity.ok(auditLogService.query(
                null,
                action,
                targetType,
                actorId,
                from != null ? from.toInstant() : null,
                to != null ? to.toInstant() : null,
                page != null ? page : 0,
                size != null ? size : 20));
    }
}
