package eu.appbahn.platform.resource.controller;

import eu.appbahn.platform.api.ResourceTypeInfo;
import eu.appbahn.platform.api.resourcetype.ResourceTypesApi;
import eu.appbahn.platform.resource.service.ResourceTypeService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ResourceTypesController implements ResourceTypesApi {

    private final ResourceTypeService resourceTypeService;

    public ResourceTypesController(ResourceTypeService resourceTypeService) {
        this.resourceTypeService = resourceTypeService;
    }

    @Override
    public ResponseEntity<List<ResourceTypeInfo>> listResourceTypes(String cluster) {
        return ResponseEntity.ok(resourceTypeService.listAvailable(cluster));
    }
}
