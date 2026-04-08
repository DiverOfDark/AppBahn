package eu.appbahn.platform.workspace.controller;

import eu.appbahn.platform.api.ProjectsApi;
import eu.appbahn.platform.api.model.CreateProjectRequest;
import eu.appbahn.platform.api.model.PagedProjectResponse;
import eu.appbahn.platform.api.model.Project;
import eu.appbahn.platform.api.model.Quota;
import eu.appbahn.platform.api.model.RegistryConfig;
import eu.appbahn.platform.api.model.UpdateMemberRequest;
import eu.appbahn.platform.api.model.UpdateProjectRequest;
import eu.appbahn.platform.common.security.AuthContextHolder;
import eu.appbahn.platform.workspace.service.ProjectService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProjectsController implements ProjectsApi {

    private final ProjectService projectService;

    public ProjectsController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public ResponseEntity<Project> createProject(CreateProjectRequest createProjectRequest) {
        var project = projectService.create(createProjectRequest, AuthContextHolder.get());
        return ResponseEntity.status(201).body(project);
    }

    @Override
    public ResponseEntity<PagedProjectResponse> listProjects(
            String workspaceSlug, Integer page, Integer size, String sort) {
        var result = projectService.list(workspaceSlug, page, size, sort, AuthContextHolder.get());
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<Project> getProject(String slug) {
        return ResponseEntity.ok(projectService.getBySlug(slug, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<Project> updateProject(String slug, UpdateProjectRequest updateProjectRequest) {
        var project = projectService.update(slug, updateProjectRequest, AuthContextHolder.get());
        return ResponseEntity.ok(project);
    }

    @Override
    public ResponseEntity<Void> deleteProject(String slug) {
        projectService.delete(slug, AuthContextHolder.get());
        return ResponseEntity.noContent().build();
    }

    // --- Role overrides ---

    @Override
    public ResponseEntity<Void> setProjectMemberRole(
            String slug, UUID userId, UpdateMemberRequest updateMemberRequest) {
        projectService.setMemberRoleOverride(slug, userId, updateMemberRequest, AuthContextHolder.get());
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> deleteProjectMemberRole(String slug, UUID userId) {
        projectService.deleteMemberRoleOverride(slug, userId, AuthContextHolder.get());
        return ResponseEntity.noContent().build();
    }

    // --- Settings ---

    @Override
    public ResponseEntity<Quota> getProjectQuota(String slug) {
        return ResponseEntity.ok(projectService.getQuota(slug, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<Quota> setProjectQuota(String slug, Quota quota) {
        return ResponseEntity.ok(projectService.setQuota(slug, quota, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<Project> setProjectRegistry(String slug, RegistryConfig registryConfig) {
        return ResponseEntity.ok(projectService.setRegistry(slug, registryConfig, AuthContextHolder.get()));
    }
}
