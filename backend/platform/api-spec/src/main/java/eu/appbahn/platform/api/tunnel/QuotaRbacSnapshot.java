package eu.appbahn.platform.api.tunnel;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Carries everything the operator's admission webhook needs to decide allow/deny locally.
 * Env↔namespace, quota caps at env/project/workspace levels, per-env current usage, RBAC
 * closure (OIDC subjects + groups resolving to EDITOR+).
 */
@Data
public class QuotaRbacSnapshot {

    @Valid
    private List<EnvironmentEntry> environments = new ArrayList<>();

    private List<String> platformAdminGroups = new ArrayList<>();

    @Valid
    private List<ProjectEntry> projects = new ArrayList<>();

    @Valid
    private List<WorkspaceEntry> workspaces = new ArrayList<>();
}
