package eu.appbahn.platform.resource.controller;

import eu.appbahn.platform.api.internal.ResourceSyncApi;
import eu.appbahn.platform.api.internal.model.FullResourceSyncRequest;
import eu.appbahn.platform.api.internal.model.ResourceSyncRequest;
import eu.appbahn.platform.resource.service.ResourceSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal")
public class InternalController implements ResourceSyncApi {

    private final ResourceSyncService resourceSyncService;

    public InternalController(ResourceSyncService resourceSyncService) {
        this.resourceSyncService = resourceSyncService;
    }

    @Override
    public ResponseEntity<Void> syncResource(ResourceSyncRequest resourceSyncRequest) {
        resourceSyncService.syncResource(resourceSyncRequest);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteResourceSync(String slug) {
        resourceSyncService.deleteResourceSync(slug);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> fullResourceSync(FullResourceSyncRequest fullResourceSyncRequest) {
        resourceSyncService.fullSync(fullResourceSyncRequest);
        return ResponseEntity.noContent().build();
    }
}
