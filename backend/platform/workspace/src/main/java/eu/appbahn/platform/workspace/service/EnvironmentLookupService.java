package eu.appbahn.platform.workspace.service;

import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.workspace.entity.EnvironmentEntity;
import eu.appbahn.platform.workspace.repository.EnvironmentRepository;
import eu.appbahn.platform.workspace.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Public read-only entry point for looking up environments and their parent hierarchy
 * from outside the workspace module. External modules (e.g. {@code resource}) talk to
 * this service instead of injecting {@link EnvironmentRepository} or
 * {@link ProjectRepository} directly.
 */
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

    /** Non-throwing variant for callers that need to handle "missing env" themselves (e.g. operator sync). */
    public Optional<EnvironmentEntity> findBySlugOptional(String slug) {
        return environmentRepository.findBySlug(slug);
    }

    /** Non-throwing variant for callers that need to handle "missing env" themselves (e.g. operator sync). */
    public Optional<EnvironmentEntity> findByIdOptional(UUID environmentId) {
        return environmentRepository.findById(environmentId);
    }

    public List<EnvironmentEntity> findByTargetCluster(String clusterName) {
        return environmentRepository.findByTargetCluster(clusterName);
    }

    public UUID getWorkspaceId(EnvironmentEntity env) {
        var project = projectRepository
                .findById(env.getProjectId())
                .orElseThrow(() -> new NotFoundException("Project not found for environment: " + env.getSlug()));
        return project.getWorkspaceId();
    }
}
