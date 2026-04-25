package eu.appbahn.platform.user.controller;

import eu.appbahn.platform.api.Cluster;
import eu.appbahn.platform.api.NetworkPolicy;
import eu.appbahn.platform.api.PagedAuditLogResponse;
import eu.appbahn.platform.api.PlatformConfig;
import eu.appbahn.platform.api.PlatformConfigBranding;
import eu.appbahn.platform.api.ResourceTypeDefinition;
import eu.appbahn.platform.api.admin.AdminApi;
import eu.appbahn.platform.api.admin.CreateClusterRequest;
import eu.appbahn.platform.api.admin.CreateNetworkPolicyRequest;
import eu.appbahn.platform.api.admin.PagedUserResponse;
import eu.appbahn.platform.api.admin.UpdateClusterRequest;
import eu.appbahn.platform.api.admin.UpdateNetworkPolicyRequest;
import eu.appbahn.platform.api.admin.UpdateResourceTypeAdminConfigRequest;
import eu.appbahn.platform.common.audit.AuditLogService;
import eu.appbahn.platform.common.exception.ForbiddenException;
import eu.appbahn.platform.common.security.AuthContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    @Override
    public ResponseEntity<NetworkPolicy> createNetworkPolicy(CreateNetworkPolicyRequest createNetworkPolicyRequest) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Void> deleteCluster(String name) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Void> deleteNetworkPolicy(UUID id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<ResourceTypeDefinition> getResourceTypeDefinition(String type) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<List<Cluster>> listClusters() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<List<NetworkPolicy>> listNetworkPolicies() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<List<ResourceTypeDefinition>> listResourceTypeDefinitions() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<PagedUserResponse> listUsers(Integer page, Integer size, String email) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Cluster> registerCluster(CreateClusterRequest createClusterRequest) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<PlatformConfig> setPlatformConfig(PlatformConfig platformConfig) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Cluster> updateCluster(String name, UpdateClusterRequest updateClusterRequest) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<NetworkPolicy> updateNetworkPolicy(UUID id, UpdateNetworkPolicyRequest req) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<ResourceTypeDefinition> updateResourceTypeAdminConfig(
            String type, UpdateResourceTypeAdminConfigRequest req) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
