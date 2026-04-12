package eu.appbahn.operator.reconciler;

import eu.appbahn.operator.client.ApiException;
import eu.appbahn.operator.client.api.ResourceSyncApi;
import eu.appbahn.operator.client.model.FullResourceSyncRequest;
import eu.appbahn.operator.client.model.ResourceSyncRequest;
import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Performs a full resource synchronisation from Kubernetes to the platform API. Runs at startup and
 * every 5 minutes as a safety net to catch missed events.
 */
@Component
public class FullSyncService {

    private static final Logger log = LoggerFactory.getLogger(FullSyncService.class);

    private final ResourceSyncApi resourceSyncApi;
    private final KubernetesClient kubernetesClient;
    private final OperatorConfig operatorConfig;

    public FullSyncService(
            ResourceSyncApi resourceSyncApi, KubernetesClient kubernetesClient, OperatorConfig operatorConfig) {
        this.resourceSyncApi = resourceSyncApi;
        this.kubernetesClient = kubernetesClient;
        this.operatorConfig = operatorConfig;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(100) // Run after OperatorStarter (JOSDK) which starts the operator on the same event
    public void onStartup() {
        log.info("Performing startup full sync");
        Thread.startVirtualThread(this::performFullSync);
    }

    @Scheduled(
            fixedDelayString = "${operator.full-sync-interval-ms:300000}",
            initialDelayString = "${operator.full-sync-initial-delay-ms:60000}")
    public void scheduledSync() {
        log.debug("Performing scheduled full sync");
        performFullSync();
    }

    synchronized void performFullSync() {
        try {
            List<ResourceCrd> resources = kubernetesClient
                    .resources(ResourceCrd.class)
                    .inAnyNamespace()
                    .list()
                    .getItems();

            List<ResourceSyncRequest> syncRequests = new ArrayList<>();
            for (ResourceCrd crd : resources) {
                syncRequests.add(ResourceSyncRequestBuilder.fromCrd(crd, operatorConfig.getClusterName()));
            }

            var fullRequest = new FullResourceSyncRequest();
            fullRequest.setClusterName(operatorConfig.getClusterName());
            fullRequest.setResources(syncRequests);

            resourceSyncApi.fullResourceSync(fullRequest);
            log.info("Full sync completed: {} resources synced", syncRequests.size());
        } catch (ApiException e) {
            log.warn("Full sync failed (HTTP {}): {}", e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("Full sync failed: {}", e.getMessage());
        }
    }
}
