package eu.appbahn.platform.resource.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.api.model.Deployment;
import eu.appbahn.platform.api.model.Resource;
import eu.appbahn.platform.api.model.ResourceTypeInfo;
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
        resource.setStatus(
                entity.getStatus() != null
                        ? Resource.StatusEnum.fromValue(entity.getStatus().name())
                        : null);
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
        try {
            deployment.setTriggeredBy(
                    Deployment.TriggeredByEnum.valueOf(entity.getTriggeredBy().name()));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown triggeredBy value '{}' for deployment {}", entity.getTriggeredBy(), entity.getId());
        }
        deployment.setStatus(Deployment.StatusEnum.fromValue(entity.getStatus().name()));
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
                info.setCategory(ResourceTypeInfo.CategoryEnum.fromValue(definition.getCategory()));
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
