package eu.appbahn.platform.resource.service;

import eu.appbahn.platform.api.Deployment;
import eu.appbahn.platform.api.resource.PagedDeploymentResponse;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.security.AuthContext;
import eu.appbahn.platform.common.util.PagedResponseUtil;
import eu.appbahn.platform.common.util.PaginationUtil;
import eu.appbahn.platform.resource.repository.DeploymentRepository;
import eu.appbahn.shared.model.MemberRole;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only Deployment audit access. Deployment rows are minted by {@code BuildLifecycleHandler}
 * (operator-driven build events) — there's no platform-side trigger anymore. To kick a rebuild,
 * users patch the ImageSource (e.g. bump an annotation) or refresh git creds.
 */
@Service
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final ResourcePermissionHelper resourcePermissionHelper;

    public DeploymentService(
            DeploymentRepository deploymentRepository, ResourcePermissionHelper resourcePermissionHelper) {
        this.deploymentRepository = deploymentRepository;
        this.resourcePermissionHelper = resourcePermissionHelper;
    }

    @Transactional(readOnly = true)
    public Deployment get(String resourceSlug, UUID deploymentId, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(resourceSlug, ctx, MemberRole.VIEWER);

        var entity = deploymentRepository
                .findByIdAndResourceSlug(deploymentId, resourceSlug)
                .orElseThrow(() -> new NotFoundException("Deployment not found: " + deploymentId));

        return ResourceEntityMapper.toApi(entity, resolved.env().getSlug());
    }

    @Transactional(readOnly = true)
    public PagedDeploymentResponse list(String resourceSlug, Integer page, Integer size, String sort, AuthContext ctx) {
        var resolved = resourcePermissionHelper.resolve(resourceSlug, ctx, MemberRole.VIEWER);
        var env = resolved.env();

        var pageable = PaginationUtil.toPageable(page, size, sort, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = deploymentRepository.findByResourceSlug(resourceSlug, pageable);

        return PagedResponseUtil.build(
                result,
                e -> ResourceEntityMapper.toApi(e, env.getSlug()),
                new PagedDeploymentResponse(),
                PagedDeploymentResponse::setContent,
                PagedDeploymentResponse::setPage,
                PagedDeploymentResponse::setSize,
                PagedDeploymentResponse::setTotalElements,
                PagedDeploymentResponse::setTotalPages);
    }
}
