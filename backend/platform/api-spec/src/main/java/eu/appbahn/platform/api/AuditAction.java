package eu.appbahn.platform.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Discrete platform mutation recorded in the audit log. */
@Schema(name = "AuditAction", enumAsRef = true)
public enum AuditAction {
    @JsonProperty("DeploymentCanceled")
    DEPLOYMENT_CANCELED,

    @JsonProperty("DeploymentRetried")
    DEPLOYMENT_RETRIED,

    @JsonProperty("DeploymentTriggered")
    DEPLOYMENT_TRIGGERED,

    @JsonProperty("EnvironmentApprovalGatesUpdated")
    ENVIRONMENT_APPROVAL_GATES_UPDATED,

    @JsonProperty("EnvironmentCreated")
    ENVIRONMENT_CREATED,

    @JsonProperty("EnvironmentDeleted")
    ENVIRONMENT_DELETED,

    @JsonProperty("EnvironmentQuotaUpdated")
    ENVIRONMENT_QUOTA_UPDATED,

    @JsonProperty("EnvironmentRegistryUpdated")
    ENVIRONMENT_REGISTRY_UPDATED,

    @JsonProperty("EnvironmentRoleOverrideRemoved")
    ENVIRONMENT_ROLE_OVERRIDE_REMOVED,

    @JsonProperty("EnvironmentRoleOverrideSet")
    ENVIRONMENT_ROLE_OVERRIDE_SET,

    @JsonProperty("EnvironmentTargetClusterUpdated")
    ENVIRONMENT_TARGET_CLUSTER_UPDATED,

    @JsonProperty("EnvironmentTokenCreated")
    ENVIRONMENT_TOKEN_CREATED,

    @JsonProperty("EnvironmentTokenDeleted")
    ENVIRONMENT_TOKEN_DELETED,

    @JsonProperty("EnvironmentUpdated")
    ENVIRONMENT_UPDATED,

    @JsonProperty("GroupMappingCreated")
    GROUP_MAPPING_CREATED,

    @JsonProperty("GroupMappingDeleted")
    GROUP_MAPPING_DELETED,

    @JsonProperty("GroupMappingUpdated")
    GROUP_MAPPING_UPDATED,

    @JsonProperty("InviteAccepted")
    INVITE_ACCEPTED,

    @JsonProperty("InviteCodeCreated")
    INVITE_CODE_CREATED,

    @JsonProperty("InviteCodeRevoked")
    INVITE_CODE_REVOKED,

    @JsonProperty("InviteDeclined")
    INVITE_DECLINED,

    @JsonProperty("InviteRedeemed")
    INVITE_REDEEMED,

    @JsonProperty("MemberAdded")
    MEMBER_ADDED,

    @JsonProperty("MemberInvited")
    MEMBER_INVITED,

    @JsonProperty("MemberRemoved")
    MEMBER_REMOVED,

    @JsonProperty("MemberUpdated")
    MEMBER_UPDATED,

    @JsonProperty("ProjectCreated")
    PROJECT_CREATED,

    @JsonProperty("ProjectDeleted")
    PROJECT_DELETED,

    @JsonProperty("ProjectQuotaUpdated")
    PROJECT_QUOTA_UPDATED,

    @JsonProperty("ProjectRegistryUpdated")
    PROJECT_REGISTRY_UPDATED,

    @JsonProperty("ProjectRoleOverrideRemoved")
    PROJECT_ROLE_OVERRIDE_REMOVED,

    @JsonProperty("ProjectRoleOverrideSet")
    PROJECT_ROLE_OVERRIDE_SET,

    @JsonProperty("ProjectUpdated")
    PROJECT_UPDATED,

    @JsonProperty("ResourceCreated")
    RESOURCE_CREATED,

    @JsonProperty("ResourceDeleted")
    RESOURCE_DELETED,

    @JsonProperty("ResourceRestarted")
    RESOURCE_RESTARTED,

    @JsonProperty("ResourceStarted")
    RESOURCE_STARTED,

    @JsonProperty("ResourceStopped")
    RESOURCE_STOPPED,

    @JsonProperty("ResourceUpdated")
    RESOURCE_UPDATED,

    @JsonProperty("WorkspaceCreated")
    WORKSPACE_CREATED,

    @JsonProperty("WorkspaceDeleted")
    WORKSPACE_DELETED,

    @JsonProperty("WorkspaceQuotaUpdated")
    WORKSPACE_QUOTA_UPDATED,

    @JsonProperty("WorkspaceRegistryUpdated")
    WORKSPACE_REGISTRY_UPDATED,

    @JsonProperty("WorkspaceSecurityUpdated")
    WORKSPACE_SECURITY_UPDATED,

    @JsonProperty("WorkspaceUpdated")
    WORKSPACE_UPDATED,

    @JsonProperty("UserPreferencesUpdated")
    USER_PREFERENCES_UPDATED
}
