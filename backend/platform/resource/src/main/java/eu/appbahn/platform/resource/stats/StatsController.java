package eu.appbahn.platform.resource.stats;

import eu.appbahn.platform.api.stats.EnvironmentStats;
import eu.appbahn.platform.api.stats.ProjectStats;
import eu.appbahn.platform.api.stats.StatsApi;
import eu.appbahn.platform.api.stats.WorkspaceStats;
import eu.appbahn.platform.common.security.AuthContextHolder;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StatsController implements StatsApi {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @Override
    public ResponseEntity<List<WorkspaceStats>> getWorkspaceStats(List<String> slugs) {
        return ResponseEntity.ok(statsService.workspaceStats(slugs, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<List<ProjectStats>> getProjectStats(String workspaceSlug) {
        return ResponseEntity.ok(statsService.projectStats(workspaceSlug, AuthContextHolder.get()));
    }

    @Override
    public ResponseEntity<List<EnvironmentStats>> getEnvironmentStats(String projectSlug) {
        return ResponseEntity.ok(statsService.environmentStats(projectSlug, AuthContextHolder.get()));
    }
}
