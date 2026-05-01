package eu.appbahn.platform.resource.controller;

import eu.appbahn.platform.common.exception.ForbiddenException;
import eu.appbahn.platform.common.security.AuthContextHolder;
import eu.appbahn.platform.resource.service.DeploymentRetentionService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for {@link DeploymentRetentionService}, used to drive the production prune path
 * from e2e tests without globally lowering the cron schedule (which would affect every test's
 * deployment-row counting). Hidden from the public OpenAPI surface — the schedule is the
 * canonical entry point; this endpoint is for operational + test use.
 */
@RestController
@RequestMapping("/api/v1/admin/deployment-retention")
@Hidden
public class DeploymentRetentionAdminController {

    private final DeploymentRetentionService service;

    public DeploymentRetentionAdminController(DeploymentRetentionService service) {
        this.service = service;
    }

    @PostMapping("/run")
    public ResponseEntity<RunResponse> run(@RequestParam(value = "max", required = false) Integer max) {
        if (!AuthContextHolder.get().platformAdmin()) {
            throw new ForbiddenException("Platform admin access required");
        }
        int effectiveMax = max != null ? max : 0;
        if (effectiveMax <= 0) {
            // Mirror what the scheduled path does: read configured max via service.prune().
            // Returning -1 signals "ran via configured policy"; caller doesn't need the count.
            service.prune();
            return ResponseEntity.ok(new RunResponse(-1));
        }
        return ResponseEntity.ok(new RunResponse(service.pruneWith(effectiveMax)));
    }

    public record RunResponse(int pruned) {}
}
