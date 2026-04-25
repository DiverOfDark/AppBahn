package eu.appbahn.operator.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.operator.tunnel.AdmissionSnapshotCache;
import eu.appbahn.operator.tunnel.OperatorEventPublisher;
import eu.appbahn.operator.tunnel.client.model.AdmissionApproved;
import eu.appbahn.operator.tunnel.client.model.AdmissionCacheMissReport;
import eu.appbahn.operator.tunnel.client.model.EnvironmentEntry;
import eu.appbahn.operator.tunnel.client.model.ResourceSyncItem;
import eu.appbahn.shared.Labels;
import eu.appbahn.shared.crd.ResourceCrd;
import eu.appbahn.shared.crd.ResourcePhase;
import eu.appbahn.shared.crd.ResourceStatusDetail;
import eu.appbahn.shared.util.DeepClone;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionRequest;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionResponse;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionResponseBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReviewBuilder;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 *   <li>Any quota dimension (resources / cpu / memory) would be exceeded at env, project,
 *       or workspace scope → deny. Dimensions whose limit is 0 at a given scope are skipped
 *       (fail-open); storage + replicas are always fail-open (no CR-side source yet).</li>
 *   <li>Otherwise allow, and emit an {@link AdmissionApproved} event so the platform seeds
 *       {@code resource_cache} with a PENDING row before the reconciler's watch fires
 *       (fast read-after-write for {@code kubectl apply}).</li>
 * </ul>
 * The operator's own ServiceAccount — resolved at startup via {@link OperatorIdentity} from the
 * projected token's {@code sub} claim — and users whose OIDC groups include a platform-admin
 * group bypass per-env RBAC + quota checks.
 */
@RestController
public class ResourceAdmissionController {

    private static final Logger log = LoggerFactory.getLogger(ResourceAdmissionController.class);

    private final AdmissionSnapshotCache admissionCache;
    private final OperatorEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final OperatorIdentity operatorIdentity;
    private final AuditEventEmitter auditEventEmitter;
    private final SelfChangeFingerprint selfChangeFingerprint;

    public ResourceAdmissionController(
            AdmissionSnapshotCache admissionCache,
            OperatorEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            OperatorIdentity operatorIdentity,
            AuditEventEmitter auditEventEmitter,
            SelfChangeFingerprint selfChangeFingerprint) {
        this.admissionCache = admissionCache;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.operatorIdentity = operatorIdentity;
        this.auditEventEmitter = auditEventEmitter;
        this.selfChangeFingerprint = selfChangeFingerprint;
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

        String user = request.getUserInfo() != null ? request.getUserInfo().getUsername() : null;
        List<String> groups =
                request.getUserInfo() != null && request.getUserInfo().getGroups() != null
                        ? request.getUserInfo().getGroups()
                        : List.of();
        boolean isOperatorSelf = isOperatorSelf(user);
        boolean isDryRun = Boolean.TRUE.equals(request.getDryRun());

        if ("DELETE".equalsIgnoreCase(request.getOperation())) {
            // K8s runs the webhook on DELETE too; no need to validate — the operator will
            // process the tombstone via its reconciler regardless.
            maybeEmitAllowAudit(request, request.getName(), isOperatorSelf, isDryRun);
            return allow(request.getUid());
        }

        // UPDATE is allowed without a snapshot: the object was already admitted at CREATE
        // time, and the operator's own reconciler issues UPDATE/PATCH for status. Blocking
        // those would deadlock: no reconciler → no snapshot → no admission.
        boolean isCreate = "CREATE".equalsIgnoreCase(request.getOperation());
        if (!isCreate) {
            maybeEmitAllowAudit(request, request.getName(), isOperatorSelf, isDryRun);
            return allow(request.getUid());
        }

        // Operator's own PATCH races the QuotaRbacCachePush: a fresh env can reach
        // pending_command faster than the next snapshot refresh. The platform has
        // already validated the env existed + quota fit when it enqueued ApplyResource,
        // so we trust the operator's own ServiceAccount call unconditionally. We still
        // emit AdmissionApproved when we do happen to know the env (cache refreshed).
        if (isOperatorSelf) {
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
            return denyWithAudit(
                    request,
                    request.getName(),
                    "operator has no admission snapshot yet; reconnect the tunnel and retry",
                    isDryRun);
        }

        Optional<EnvironmentEntry> entryOpt = admissionCache.entryForNamespace(request.getNamespace());
        if (entryOpt.isEmpty()) {
            emitCacheMiss(request, "namespace not in snapshot");
            return denyWithAudit(
                    request,
                    request.getName(),
                    "namespace " + request.getNamespace() + " not known to the platform",
                    isDryRun);
        }
        EnvironmentEntry entry = entryOpt.get();

        if (!isPlatformAdmin(groups) && !isAuthorisedForEnv(user, groups, entry)) {
            emitCacheMiss(request, "user not authorised for env");
            return denyWithAudit(
                    request,
                    request.getName(),
                    "user " + (user == null ? "<anonymous>" : user) + " is not authorised to apply resources in "
                            + entry.getSlug(),
                    isDryRun);
        }

        ResourceCrd crd = parseObject(request.getObject());
        if (crd == null) {
            return denyWithAudit(
                    request, request.getName(), "admission request missing or unparseable `object`", isDryRun);
        }

        var incoming = QuotaEnforcement.incomingFromConfig(
                crd.getSpec() == null ? null : crd.getSpec().getConfig());

        Optional<QuotaEnforcement.Denial> denial = QuotaEnforcement.checkEnv(entry, incoming);
        String projectSlug = entry.getProjectSlug();
        if (denial.isEmpty() && projectSlug != null && !projectSlug.isEmpty()) {
            var project = admissionCache.projectEntry(projectSlug);
            if (project.isPresent()) {
                denial = QuotaEnforcement.checkProject(
                        project.get(), admissionCache.envEntriesInProject(projectSlug), incoming);
            }
        }
        String workspaceSlug = entry.getWorkspaceSlug();
        if (denial.isEmpty() && workspaceSlug != null && !workspaceSlug.isEmpty()) {
            var workspace = admissionCache.workspaceEntry(workspaceSlug);
            if (workspace.isPresent()) {
                denial = QuotaEnforcement.checkWorkspace(
                        workspace.get(), admissionCache.envEntriesInWorkspace(workspaceSlug), incoming);
            }
        }
        if (denial.isPresent()) {
            return denyWithAudit(request, request.getName(), denial.get().message(), isDryRun);
        }

        AdmissionReview response = allow(request.getUid());
        emitApproved(entry.getSlug(), crd);
        maybeEmitAllowAudit(request, request.getName(), false, isDryRun);
        return response;
    }

    private boolean isOperatorSelf(String user) {
        String operatorUser = operatorIdentity.username().orElse(null);
        return operatorUser != null && operatorUser.equals(user);
    }

    /** Emit audit for allowed admissions unless filtered by a loop guard. */
    private void maybeEmitAllowAudit(
            AdmissionRequest request, String targetSlug, boolean isOperatorSelf, boolean isDryRun) {
        if (isOperatorSelf || isDryRun) {
            return;
        }
        // Layer 3: belt-and-suspenders self-change guard. The resourceVersion on the incoming
        // object is the pre-change version; we compare it against what the operator last wrote.
        String rv = resourceVersionOf(request);
        if (rv != null && selfChangeFingerprint.isSelfChange(request.getNamespace(), request.getName(), rv)) {
            return;
        }
        auditEventEmitter.emitAllow(request, targetSlug);
    }

    /** Deny + emit the deny audit in one shot. */
    private AdmissionReview denyWithAudit(
            AdmissionRequest request, String targetSlug, String reason, boolean isDryRun) {
        if (!isDryRun) {
            auditEventEmitter.emitDeny(request, targetSlug, reason);
        }
        return deny(request.getUid(), reason);
    }

    /** Best-effort extraction of metadata.resourceVersion from the admission request's object. */
    private String resourceVersionOf(AdmissionRequest request) {
        Object obj = request.getObject() != null ? request.getObject() : request.getOldObject();
        if (!(obj instanceof java.util.Map<?, ?> map)) {
            return null;
        }
        Object metadata = map.get("metadata");
        if (!(metadata instanceof java.util.Map<?, ?> metaMap)) {
            return null;
        }
        Object rv = metaMap.get("resourceVersion");
        return rv == null ? null : rv.toString();
    }

    private boolean isPlatformAdmin(Collection<String> userGroups) {
        List<String> adminGroups = admissionCache.platformAdminGroups();
        if (adminGroups.isEmpty() || userGroups == null || userGroups.isEmpty()) {
            return false;
        }
        return userGroups.stream().anyMatch(adminGroups::contains);
    }

    private boolean isAuthorisedForEnv(String user, Collection<String> userGroups, EnvironmentEntry entry) {
        if (user != null && entry.getAllowedUserSubjects().contains(user)) {
            return true;
        }
        if (userGroups == null || userGroups.isEmpty()) {
            return false;
        }
        return userGroups.stream().anyMatch(entry.getAllowedOidcGroups()::contains);
    }

    /**
     * Best-effort emission of an {@link AdmissionApproved} event carrying the admitted spec so
     * the platform can seed {@code resource_cache} with the fresh state before the reconciler's
     * watch fires. A publish failure does not block the admit — the reconciler's own sync will
     * eventually catch up.
     */
    private void emitApproved(String envSlug, ResourceCrd crd) {
        try {
            // Work off a deep copy — the caller's CRD must stay pristine.
            ResourceCrd snapshot = DeepClone.of(crd, objectMapper);
            if (snapshot.getMetadata() == null) {
                snapshot.setMetadata(new ObjectMeta());
            }
            // Stamp the env-slug label so downstream doesn't need to resolve it from namespace.
            if (envSlug != null && !envSlug.isBlank()) {
                Map<String, String> labels = snapshot.getMetadata().getLabels();
                if (labels == null) {
                    labels = new HashMap<>();
                    snapshot.getMetadata().setLabels(labels);
                }
                labels.putIfAbsent(Labels.ENVIRONMENT_SLUG_KEY, envSlug);
            }
            // Force PENDING — the CR was just admitted, no operator-side status yet.
            ResourceStatusDetail status = new ResourceStatusDetail();
            status.setPhase(ResourcePhase.PENDING);
            snapshot.setStatus(status);
            var item = new ResourceSyncItem();
            item.setResource(snapshot);
            if (snapshot.getMetadata() != null) {
                if (snapshot.getMetadata().getGeneration() != null) {
                    item.setGeneration(snapshot.getMetadata().getGeneration());
                }
                if (snapshot.getMetadata().getResourceVersion() != null) {
                    item.setResourceVersion(snapshot.getMetadata().getResourceVersion());
                }
            }
            var event = new AdmissionApproved();
            event.setItem(item);
            eventPublisher.emit(event);
            log.debug("Emitted AdmissionApproved for {}", snapshot.getMetadata().getName());
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
            var report = new AdmissionCacheMissReport();
            report.setNamespace(request.getNamespace() != null ? request.getNamespace() : "");
            report.setUserOidcSubject(user);
            report.setReason(reason);
            eventPublisher.emit(report);
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
