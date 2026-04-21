package eu.appbahn.operator.reconciler;

import eu.appbahn.operator.tunnel.OperatorEventPublisher;
import eu.appbahn.shared.crd.ResourceCrd;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pushes every Resource CR in-cluster to the platform via {@code PushEvents} full-sync
 * chunks. Safety net against dropped reconcile events.
 */
@Component
public class FullSyncService {

    private static final Logger log = LoggerFactory.getLogger(FullSyncService.class);

    private final OperatorEventPublisher eventPublisher;
    private final KubernetesClient kubernetesClient;

    public FullSyncService(OperatorEventPublisher eventPublisher, KubernetesClient kubernetesClient) {
        this.eventPublisher = eventPublisher;
        this.kubernetesClient = kubernetesClient;
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

    public synchronized void performFullSync() {
        try {
            List<ResourceCrd> resources = kubernetesClient
                    .resources(ResourceCrd.class)
                    .inAnyNamespace()
                    .list()
                    .getItems();

            eventPublisher.emitFullSync(resources);
            log.info("Full sync completed: {} resources synced", resources.size());
        } catch (Exception e) {
            log.warn("Full sync failed: {}", e.getMessage());
        }
    }
}
