package eu.appbahn.platform.api.project;

import eu.appbahn.platform.api.Project;
import eu.appbahn.platform.api.Quota;
import eu.appbahn.platform.api.RegistryConfig;
import eu.appbahn.platform.api.UpdateMemberRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@Tag(name = "Projects")
public interface ProjectsApi {
    /**
     * POST /projects : Create project
     *
     * @param createProjectRequest  (required)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Conflict (status code 409)
     *         or Unprocessable entity (status code 422)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/projects",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Project> createProject(@Valid @RequestBody CreateProjectRequest createProjectRequest);
    /**
     * DELETE /projects/{slug} : Delete project
     *
     * @param slug  (required)
     * @return Project deleted (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Conflict (status code 409)
     */
    @RequestMapping(
            method = RequestMethod.DELETE,
            value = "/projects/{slug}",
            produces = {"application/json"})
    ResponseEntity<Void> deleteProject(@PathVariable("slug") String slug);
    /**
     * DELETE /projects/{slug}/members/{user_id}/role : Remove project-level role override
     *
     * @param slug  (required)
     * @param userId  (required)
     * @return Role override removed (status code 204)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.DELETE,
            value = "/projects/{slug}/members/{user_id}/role",
            produces = {"application/json"})
    ResponseEntity<Void> deleteProjectMemberRole(
            @PathVariable("slug") String slug, @PathVariable("user_id") UUID userId);
    /**
     * GET /projects/{slug} : Get project details
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/projects/{slug}",
            produces = {"application/json"})
    ResponseEntity<Project> getProject(@PathVariable("slug") String slug);
    /**
     * GET /projects/{slug}/quota : Get project quota
     *
     * @param slug  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/projects/{slug}/quota",
            produces = {"application/json"})
    ResponseEntity<Quota> getProjectQuota(@PathVariable("slug") String slug);
    /**
     * GET /projects : List projects
     *
     * @param workspaceSlug  (optional)
     * @param page  (optional)
     * @param size  (optional)
     * @param sort  (optional)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/projects",
            produces = {"application/json"})
    ResponseEntity<PagedProjectResponse> listProjects(
            @Valid @RequestParam(value = "workspaceSlug", required = false) @Nullable String workspaceSlug,
            @Valid @RequestParam(value = "page", required = false) @Nullable Integer page,
            @Valid @RequestParam(value = "size", required = false) @Nullable Integer size,
            @Valid @RequestParam(value = "sort", required = false) @Nullable String sort);
    /**
     * PUT /projects/{slug}/members/{user_id}/role : Set project-level role override
     *
     * @param slug  (required)
     * @param userId  (required)
     * @param updateMemberRequest  (optional)
     * @return Role override set (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Unprocessable entity (status code 422)
     */
    @RequestMapping(
            method = RequestMethod.PUT,
            value = "/projects/{slug}/members/{user_id}/role",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Void> setProjectMemberRole(
            @PathVariable("slug") String slug,
            @PathVariable("user_id") UUID userId,
            @Valid @RequestBody(required = false) @Nullable UpdateMemberRequest updateMemberRequest);
    /**
     * PATCH /projects/{slug}/quota : Set project quota
     *
     * @param slug  (required)
     * @param quota  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.PATCH,
            value = "/projects/{slug}/quota",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Quota> setProjectQuota(
            @PathVariable("slug") String slug, @Valid @RequestBody(required = false) @Nullable Quota quota);
    /**
     * PUT /projects/{slug}/registry : Set project registry configuration
     *
     * @param slug  (required)
     * @param registryConfig  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.PUT,
            value = "/projects/{slug}/registry",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Project> setProjectRegistry(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable RegistryConfig registryConfig);
    /**
     * PATCH /projects/{slug} : Update project
     *
     * @param slug  (required)
     * @param updateProjectRequest  (optional)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     *         or Unprocessable entity (status code 422)
     */
    @RequestMapping(
            method = RequestMethod.PATCH,
            value = "/projects/{slug}",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<Project> updateProject(
            @PathVariable("slug") String slug,
            @Valid @RequestBody(required = false) @Nullable UpdateProjectRequest updateProjectRequest);
}
