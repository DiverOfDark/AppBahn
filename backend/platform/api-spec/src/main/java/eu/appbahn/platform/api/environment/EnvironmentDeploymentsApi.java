package eu.appbahn.platform.api.environment;

import eu.appbahn.platform.api.resource.PagedDeploymentResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
@Tag(name = "Environments")
public interface EnvironmentDeploymentsApi {
    /**
     * GET /environments/{slug}/deployments : ListEnvironmentDeployments — recent build/deploy
     * audit rows across every resource in the environment, ordered by {@code createdAt DESC}.
     * Pass {@code limit=1} to fetch the single most recent row (the "latest pipeline" panel
     * use case); larger limits surface a flat activity feed.
     *
     * @param slug environment slug (required)
     * @param limit max number of rows to return, 1..100 (default 20)
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/environments/{slug}/deployments",
            produces = {"application/json"})
    ResponseEntity<PagedDeploymentResponse> listEnvironmentDeployments(
            @PathVariable("slug") String slug,
            @Valid @RequestParam(value = "limit", required = false) @Nullable @Min(1) @Max(100) Integer limit);
}
