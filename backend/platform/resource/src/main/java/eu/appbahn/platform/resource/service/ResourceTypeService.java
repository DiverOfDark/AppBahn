package eu.appbahn.platform.resource.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.model.ResourceTypeInfo;
import eu.appbahn.platform.resource.entity.ResourceTypeAvailabilityEntity;
import eu.appbahn.platform.resource.repository.ResourceTypeAvailabilityRepository;
import eu.appbahn.platform.resource.repository.ResourceTypeDefinitionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceTypeService {

    private final ResourceTypeDefinitionRepository definitionRepository;
    private final ResourceTypeAvailabilityRepository availabilityRepository;
    private final ObjectMapper objectMapper;

    public ResourceTypeService(
            ResourceTypeDefinitionRepository definitionRepository,
            ResourceTypeAvailabilityRepository availabilityRepository,
            ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository;
        this.availabilityRepository = availabilityRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ResourceTypeInfo> listAvailable(String cluster) {
        var definitions = definitionRepository.findAll();
        Map<String, Boolean> availabilityMap;

        if (cluster != null && !cluster.isBlank()) {
            availabilityMap = availabilityRepository.findByClusterName(cluster).stream()
                    .collect(Collectors.toMap(
                            ResourceTypeAvailabilityEntity::getType, ResourceTypeAvailabilityEntity::isAvailable));
        } else {
            availabilityMap = Map.of();
        }

        List<ResourceTypeInfo> result = new ArrayList<>();
        for (var def : definitions) {
            boolean available = availabilityMap.getOrDefault(def.getType(), false);
            result.add(ResourceEntityMapper.toApi(def, available, objectMapper));
        }
        return result;
    }
}
