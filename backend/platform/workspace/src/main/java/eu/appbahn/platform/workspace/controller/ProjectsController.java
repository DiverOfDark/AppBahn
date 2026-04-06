package eu.appbahn.platform.workspace.controller;

import eu.appbahn.platform.api.ProjectsApi;
import eu.appbahn.platform.api.model.CreateProjectRequest;
import eu.appbahn.platform.api.model.PagedProjectResponse;
import eu.appbahn.platform.api.model.Project;
import eu.appbahn.platform.api.model.Quota;
import eu.appbahn.platform.api.model.RegistryConfig;
import eu.appbahn.platform.api.model.UpdateProjectRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectsController implements ProjectsApi {

    @Override
    public ResponseEntity<Project> createProject(CreateProjectRequest createProjectRequest) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Void> deleteProject(String slug) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Project> getProject(String slug) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Quota> getProjectQuota(String slug) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<PagedProjectResponse> listProjects(String workspaceSlug, Integer page, Integer size, String sort) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Quota> setProjectQuota(String slug, Quota quota) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Project> setProjectRegistry(String slug, RegistryConfig registryConfig) {
        return ResponseEntity.status(501).build();
    }

    @Override
    public ResponseEntity<Project> updateProject(String slug, UpdateProjectRequest updateProjectRequest) {
        return ResponseEntity.status(501).build();
    }
}
