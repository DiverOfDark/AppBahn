package eu.appbahn.operator.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.operator.tunnel.AdmissionSnapshotCache;
import eu.appbahn.operator.tunnel.OperatorEventPublisher;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.tunnel.v1.AdmissionApproved;
import eu.appbahn.tunnel.v1.AdmissionCacheMissReport;
import eu.appbahn.tunnel.v1.OperatorEvent;
import eu.appbahn.tunnel.v1.QuotaRbacSnapshot;
import eu.appbahn.tunnel.v1.ResourceSyncItem;
import eu.appbahn.tunnel.wire.ResourceWireMapper;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionResponse;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionResponseBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReviewBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Validating admission webhook for Resource CRDs. Fail-closed semantics on CREATE:
 * <ul>
 *   <li>No snapshot ingested yet → deny (tunnel has only just come up). Emits an
 *       {@link AdmissionCacheMissReport} so the platform force-pushes the current snapshot.</li>
 *   <li>Namespace not present in the snapshot → deny + cache-miss report.</li>
 *   <li>User not authorised on the target env (not in {@code allowed_user_subjects}, no
 *       group intersecting {@code allowed_oidc_groups} and not a platform admin) → deny.</li>
 *   <li>Env-level {@code max_resources} would be exceeded → deny.</li>
 *   <li>Otherwise allow, and emit an {@link AdmissionApproved} event so the platform seeds
 *       {@code resource_cache} with a PENDING row before the reconciler's watch fires
 *       (fast read-after-write for {@code kubectl apply}).</li>
 * </ul>
 * The operator's own ServiceAccount ({@code system:serviceaccount:appbahn-system:*}) and users
 * whose OIDC groups include a platform-admin group bypass per-env RBAC + quota checks.
 * CPU/memory/storage/replicas quota dimensions remain on the platform-side REST path.
 */
@RestController
public class ResourceAdmissionController {

    private static final Logger log = LoggerFactory.getLogger(ResourceAdmissionController.class);

    private final AdmissionSnapshotCache admissionCache;
    private final OperatorEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public ResourceAdmissionController(
            AdmissionSnapshotCache admissionCache, OperatorEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.admissionCache = admissionCache;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @PostMapping(
            path = "/validate-appbahn-eu-v1-resource",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AdmissionReview validate(@RequestBody AdmissionReview review) {
        AdmissionRequest request = review.getRequest();
        log.debug(
                "Admission review: {} {} {}/{}",
                request.getOperation(),
                request.getUid(),
                request.getNamespace(),
                request.getName());

        if ("DELETE".equalsIgnoreCase(request.getOperation())) {
            // K8s runs the webhook on DELETE too; no need to validate — the operator will
            // process the tombstone via its reconciler regardless.
            return allow(request.getUid());
        }

        // UPDATE is allowed without a snapshot: the object was already admitted at CREATE
        // time, and the operator's own reconciler issues UPDATE/PATCH for status. Blocking
        // those would deadlock: no reconciler → no snapshot → no admission.
        boolean isCreate = "CREATE".equalsIgnoreCase(request.getOperation());
        if (!isCreate) {
            return allow(request.getUid());
        }

        // Operator's own PATCH races the QuotaRbacCachePush: a fresh env can reach
        // pending_command faster than the next snapshot refresh. The platform has
        // already validated the env existed + quota fit when it enqueued ApplyResource,
        // so we trust the operator's own ServiceAccount call unconditionally. We still
        // emit AdmissionApproved when we do happen to know the env (cache refreshed).
        String user = request.getUserInfo() != null ? request.getUserInfo().getUsername() : null;
        List<String> groups =
                request.getUserInfo() != null && request.getUserInfo().getGroups() != null
                        ? request.getUserInfo().getGroups()
                        : List.of();
        if (user != null && user.startsWith("system:serviceaccount:appbahn-system:")) {
            Optional<String> envSlug = admissionCache.environmentSlugForNamespace(request.getNamespace());
            if (envSlug.isPresent()) {
                AdmissionReview response = allow(request.getUid());
                ResourceCrd crd = parseObject(request.getObject());
                if (crd != null) {
                    emitApproved(envSlug.get(), crd);
                }
                return response;
            }
            return allow(request.getUid());
        }

        if (!admissionCache.hasSnapshot()) {
            emitCacheMiss(request, "no snapshot");
            return deny(request.getUid(), "operator has no admission snapshot yet; reconnect the tunnel and retry");
        }

        Optional<QuotaRbacSnapshot.EnvironmentEntry> entryOpt =
                admissionCache.entryForNamespace(request.getNamespace());
        if (entryOpt.isEmpty()) {
            emitCacheMiss(request, "namespace not in snapshot");
            return deny(request.getUid(), "namespace " + request.getNamespace() + " not known to the platform");
        }
        QuotaRbacSnapshot.EnvironmentEntry entry = entryOpt.get();

        if (!isPlatformAdmin(groups) && !isAuthorisedForEnv(user, groups, entry)) {
            emitCacheMiss(request, "user not authorised for env");
            return deny(
                    request.getUid(),
                    "user " + (user == null ? "<anonymous>" : user) + " is not authorised to apply resources in "
                            + entry.getSlug());
        }

        if (entry.getMaxResources() > 0 && entry.getCurrentResources() >= entry.getMaxResources()) {
            return deny(
                    request.getUid(),
                    "environment " + entry.getSlug() + " resource quota exceeded (" + entry.getCurrentResources() + "/"
                            + entry.getMaxResources() + ")");
        }

        ResourceCrd crd = parseObject(request.getObject());
        if (crd == null) {
            return deny(request.getUid(), "admission request missing or unparseable `object`");
        }

        AdmissionReview response = allow(request.getUid());
        emitApproved(entry.getSlug(), crd);
        return response;
    }

    private boolean isPlatformAdmin(Collection<String> userGroups) {
        List<String> adminGroups = admissionCache.platformAdminGroups();
        if (adminGroups.isEmpty() || userGroups == null || userGroups.isEmpty()) {
            return false;
        }
        return userGroups.stream().anyMatch(adminGroups::contains);
    }

    private boolean isAuthorisedForEnv(
            String user, Collection<String> userGroups, QuotaRbacSnapshot.EnvironmentEntry entry) {
        if (user != null && entry.getAllowedUserSubjectsList().contains(user)) {
            return true;
        }
        if (userGroups == null || userGroups.isEmpty()) {
            return false;
        }
        return userGroups.stream().anyMatch(entry.getAllowedOidcGroupsList()::contains);
    }

    /**
     * Best-effort emission of an {@link AdmissionApproved} event carrying the admitted spec so
     * the platform can seed {@code resource_cache} with the fresh state before the reconciler's
     * watch fires. A publish failure does not block the admit — the reconciler's own sync will
     * eventually catch up.
     */
    private void emitApproved(String envSlug, ResourceCrd crd) {
        try {
            // Force PENDING — the CR was just admitted, no operator-side status yet.
            var resource = ResourceWireMapper.toResource(crd, envSlug).toBuilder()
                    .setStatus("PENDING")
                    .clearStatusDetail()
                    .build();
            long generation = crd.getMetadata() != null && crd.getMetadata().getGeneration() != null
                    ? crd.getMetadata().getGeneration()
                    : 0L;
            String resourceVersion =
                    crd.getMetadata() != null && crd.getMetadata().getResourceVersion() != null
                            ? crd.getMetadata().getResourceVersion()
                            : "";
            ResourceSyncItem item = ResourceSyncItem.newBuilder()
                    .setResource(resource)
                    .setGeneration(generation)
                    .setResourceVersion(resourceVersion)
                    .build();
            OperatorEvent event = OperatorEvent.newBuilder()
                    .setAdmissionApproved(
                            AdmissionApproved.newBuilder().setItem(item).build())
                    .build();
            eventPublisher.emit(event);
            log.debug("Emitted AdmissionApproved for {}", resource.getSlug());
        } catch (Exception e) {
            log.warn("Failed to emit AdmissionApproved: {}", e.getMessage());
        }
    }

    /**
     * Best-effort emission of an {@link AdmissionCacheMissReport} so the platform can push a
     * fresher {@code QuotaRbacCachePush}. Swallowed failures: if the tunnel is down the deny
     * still stands, and the operator's next reconnect refreshes the snapshot anyway.
     */
    private void emitCacheMiss(AdmissionRequest request, String reason) {
        try {
            String user = request.getUserInfo() != null && request.getUserInfo().getUsername() != null
                    ? request.getUserInfo().getUsername()
                    : "";
            AdmissionCacheMissReport report = AdmissionCacheMissReport.newBuilder()
                    .setNamespace(request.getNamespace() != null ? request.getNamespace() : "")
                    .setUserOidcSubject(user)
                    .setReason(reason)
                    .build();
            eventPublisher.emit(OperatorEvent.newBuilder()
                    .setAdmissionCacheMissReport(report)
                    .build());
        } catch (Exception e) {
            log.debug("Failed to emit AdmissionCacheMissReport: {}", e.getMessage());
        }
    }

    /**
     * Convert fabric8's untyped {@code Object} (a {@code Map<String, Object>}) into our
     * strict {@link ResourceCrd} via Jackson; returns null on absent/unparseable input.
     */
    private ResourceCrd parseObject(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(raw, ResourceCrd.class);
        } catch (IllegalArgumentException e) {
            log.warn("Could not parse admission object as ResourceCrd: {}", e.getMessage());
            return null;
        }
    }

    private AdmissionReview allow(String uid) {
        AdmissionResponse response =
                new AdmissionResponseBuilder().withUid(uid).withAllowed(true).build();
        return new AdmissionReviewBuilder().withResponse(response).build();
    }

    private AdmissionReview deny(String uid, String reason) {
        log.info("Admission denied: {}", reason);
        Status status = new StatusBuilder().withCode(403).withMessage(reason).build();
        AdmissionResponse response = new AdmissionResponseBuilder()
                .withUid(uid)
                .withAllowed(false)
                .withStatus(status)
                .build();
        return new AdmissionReviewBuilder().withResponse(response).build();
    }
}
