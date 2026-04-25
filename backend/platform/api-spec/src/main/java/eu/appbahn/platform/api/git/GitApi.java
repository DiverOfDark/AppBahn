package eu.appbahn.platform.api.git;

import eu.appbahn.platform.api.BuildDetectionJob;
import eu.appbahn.platform.api.GitRepo;
import eu.appbahn.platform.api.GitValidationResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@Tag(name = "Git")
public interface GitApi {
    /**
     * GET /git/detect-build/{job_id} : GetBuildDetectionStatus
     *
     * @param jobId  (required)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/git/detect-build/{job_id}",
            produces = {"application/json"})
    ResponseEntity<BuildDetectionJob> getBuildDetectionStatus(@PathVariable("job_id") UUID jobId);
    /**
     * POST /git/repos : ListGitRepos
     *
     * @param gitAuthRequest  (required)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/git/repos",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<List<GitRepo>> listGitRepos(@Valid @RequestBody GitAuthRequest gitAuthRequest);
    /**
     * POST /git/detect-build : StartBuildDetection
     *
     * @param detectBuildRequest  (required)
     * @return Build detection job started (status code 202)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/git/detect-build",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<BuildDetectionJobCreated> startBuildDetection(
            @Valid @RequestBody DetectBuildRequest detectBuildRequest);
    /**
     * POST /git/validate : ValidateGitRepo
     *
     * @param validateGitRepoRequest  (required)
     * @return Success (status code 200)
     *         or Bad request (status code 400)
     *         or Unauthorized (status code 401)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = "/git/validate",
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<GitValidationResult> validateGitRepo(
            @Valid @RequestBody ValidateGitRepoRequest validateGitRepoRequest);
}
