package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.api.model.Environment;
import eu.appbahn.platform.api.model.Project;
import eu.appbahn.platform.api.model.Workspace;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.entity.ProjectEntity;
import eu.appbahn.platform.workspace.entity.WorkspaceEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class EntityMapper {

    private EntityMapper() {}

    public static Workspace toApi(WorkspaceEntity entity) {
        var ws = new Workspace();
        ws.setId(entity.getId());
        ws.setName(entity.getName());
        ws.setSlug(entity.getSlug());
        ws.setCreatedAt(toOffset(entity.getCreatedAt()));
        ws.setUpdatedAt(toOffset(entity.getUpdatedAt()));
        return ws;
    }

    public static Project toApi(ProjectEntity entity, String workspaceSlug) {
        var proj = new Project();
        proj.setId(entity.getId());
        proj.setName(entity.getName());
        proj.setSlug(entity.getSlug());
        proj.setWorkspaceSlug(workspaceSlug);
        proj.setCreatedAt(toOffset(entity.getCreatedAt()));
        proj.setUpdatedAt(toOffset(entity.getUpdatedAt()));
        return proj;
    }

    public static Environment toApi(EnvironmentEntity entity, String projectSlug) {
        var env = new Environment();
        env.setId(entity.getId());
        env.setName(entity.getName());
        env.setSlug(entity.getSlug());
        env.setProjectSlug(projectSlug);
        env.setDescription(entity.getDescription());
        env.setTargetCluster(entity.getTargetCluster());
        env.setCreatedAt(toOffset(entity.getCreatedAt()));
        env.setUpdatedAt(toOffset(entity.getUpdatedAt()));
        return env;
    }

    private static OffsetDateTime toOffset(java.time.Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }
}
