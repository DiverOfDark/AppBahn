package eu.appbahn.platform.api.stats;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Bulk-stats endpoints — one request per page, instead of N+1 fan-out from the SPA.
 * Server-side rollups; permissions are evaluated per-slug, so unauthorised slugs are
 * silently dropped from the response rather than failing the whole request.
 */
@Validated
@Tag(name = "Stats")
public interface StatsApi {

    /**
     * GET /workspaces/stats : Bulk rollup counters for one or more workspaces.
     *
     * @param slugs Workspace slugs to summarise (required, max 100).
     * @return One {@link WorkspaceStats} per readable slug. Slugs the caller cannot view
     *         are omitted; unknown slugs are omitted.
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/workspaces/stats",
            produces = {"application/json"})
    ResponseEntity<List<WorkspaceStats>> getWorkspaceStats(
            @Valid @NotEmpty @Size(max = 100) @RequestParam(value = "slugs") List<String> slugs);

    /**
     * GET /projects/stats : Bulk rollup counters for every project in a workspace.
     *
     * @param workspaceSlug Workspace whose projects to summarise (required).
     * @return One {@link ProjectStats} per project. Empty list if the workspace has no projects.
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/projects/stats",
            produces = {"application/json"})
    ResponseEntity<List<ProjectStats>> getProjectStats(
            @Valid @NotBlank @RequestParam(value = "workspaceSlug") String workspaceSlug);

    /**
     * GET /environments/stats : Bulk rollup counters for every environment in a project.
     *
     * @param projectSlug Project whose environments to summarise (required).
     * @return One {@link EnvironmentStats} per environment. Empty list if the project has none.
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/environments/stats",
            produces = {"application/json"})
    ResponseEntity<List<EnvironmentStats>> getEnvironmentStats(
            @Valid @NotBlank @RequestParam(value = "projectSlug") String projectSlug);
}
