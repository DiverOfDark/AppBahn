package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.platform.workspace.repository.ProjectRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EnvironmentLookupService {

    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;

    public EnvironmentLookupService(EnvironmentRepository environmentRepository, ProjectRepository projectRepository) {
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
    }

    public EnvironmentEntity findById(UUID environmentId) {
        return environmentRepository
                .findById(environmentId)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + environmentId));
    }

    public EnvironmentEntity findBySlug(String slug) {
        return environmentRepository
                .findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + slug));
    }

    public UUID getWorkspaceId(EnvironmentEntity env) {
        var project = projectRepository
                .findById(env.getProjectId())
                .orElseThrow(() -> new NotFoundException("Project not found for environment: " + env.getSlug()));
        return project.getWorkspaceId();
    }
}
