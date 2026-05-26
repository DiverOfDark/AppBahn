package eu.appbahn.platform.api.cluster;

import eu.appbahn.platform.api.ClusterCapacity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Validated
@Tag(name = "Clusters")
public interface ClustersApi {

    /**
     * GET /clusters/{slug}/capacity : GetClusterCapacity — aggregate CPU + memory headroom
     * across all schedulable nodes in the cluster, queried live from the operator.
     *
     * <p>Authorisation: any caller with at least one environment role on an environment
     * targeting this cluster. Cluster admins always pass.
     *
     * @param slug cluster name
     * @return Success (status code 200)
     *         or Unauthorized (status code 401)
     *         or Forbidden (status code 403)
     *         or Not found (status code 404) — cluster unknown or operator unreachable
     *         or Gateway timeout (status code 504) — operator did not answer within the budget
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/clusters/{slug}/capacity",
            produces = {"application/json"})
    ResponseEntity<ClusterCapacity> getClusterCapacity(@PathVariable("slug") String slug);
}
