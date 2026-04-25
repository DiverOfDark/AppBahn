package eu.appbahn.platform.resource.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.Deployment;
import eu.appbahn.platform.api.Resource;
import eu.appbahn.platform.api.ResourceCategory;
import eu.appbahn.platform.api.ResourceTypeInfo;
import eu.appbahn.platform.resource.entity.DeploymentEntity;
import eu.appbahn.platform.resource.entity.ResourceCacheEntity;
import eu.appbahn.platform.resource.entity.ResourceTypeDefinitionEntity;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourceEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(ResourceEntityMapper.class);

    private ResourceEntityMapper() {}

    public static Resource toApi(ResourceCacheEntity entity, String environmentSlug, ObjectMapper objectMapper) {
        var resource = new Resource();
        resource.setSlug(entity.getSlug());
        resource.setName(entity.getName());
        resource.setType(entity.getType());
        resource.setEnvironmentSlug(environmentSlug);
        resource.setConfig(entity.getConfig());
        if (entity.getLinks() != null && !entity.getLinks().isEmpty()) {
            resource.setLinks(entity.getLinks());
        }
        resource.setStatus(entity.getStatus());
        resource.setStatusDetail(entity.getStatusDetail());
        resource.setLastSyncedAt(toOffset(entity.getLastSyncedAt()));
        resource.setCreatedAt(toOffset(entity.getCreatedAt()));
        resource.setUpdatedAt(toOffset(entity.getUpdatedAt()));
        return resource;
    }

    public static Deployment toApi(DeploymentEntity entity, String environmentSlug) {
        var deployment = new Deployment();
        deployment.setId(entity.getId());
        deployment.setResourceSlug(entity.getResourceSlug());
        deployment.setEnvironmentSlug(environmentSlug);
        deployment.setSourceRef(entity.getSourceRef());
        deployment.setImageRef(entity.getImageRef());
        deployment.setTriggeredBy(entity.getTriggeredBy());
        deployment.setStatus(entity.getStatus());
        deployment.setIsPrimary(entity.isPrimary());
        deployment.setSourceDeploymentId(entity.getSourceDeploymentId());
        deployment.setCreatedAt(toOffset(entity.getCreatedAt()));
        deployment.setUpdatedAt(toOffset(entity.getUpdatedAt()));
        return deployment;
    }

    public static ResourceTypeInfo toApi(
            ResourceTypeDefinitionEntity defEntity, boolean available, ObjectMapper objectMapper) {
        var info = new ResourceTypeInfo();
        info.setType(defEntity.getType());
        var definition = defEntity.getDefinition();
        if (definition != null) {
            info.setDisplayName(definition.getDisplayName());
            info.setDescription(definition.getDescription());
            if (definition.getCategory() != null) {
                ResourceCategory category = ResourceCategory.fromValue(definition.getCategory());
                if (category == null) {
                    log.warn(
                            "Unknown resource type category '{}' for type '{}'",
                            definition.getCategory(),
                            defEntity.getType());
                } else {
                    info.setCategory(category);
                }
            }
            if (definition.getConfigSchema() != null) {
                @SuppressWarnings("unchecked")
                var schemaMap = objectMapper.convertValue(definition.getConfigSchema(), Map.class);
                info.setConfigSchema(schemaMap);
            }
        }
        info.setAvailable(available);
        return info;
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }
}
