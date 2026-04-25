package eu.appbahn.platform.resource.service;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.common.exception.ValidationException;
import eu.appbahn.shared.crd.*;
import eu.appbahn.shared.util.DeepClone;
import io.fabric8.kubernetes.api.model.Quantity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

/** Handles JSON Merge Patch semantics for {@link ResourceConfig}. */
final class ResourceConfigMerger {

    private ResourceConfigMerger() {}

    /**
     * Check that immutable config fields have not been changed. The patch source type (if present)
     * must match the existing source type.
     */
    static void checkImmutableSourceType(ResourceConfig existing, JsonNode patchSourceNode) {
        if (patchSourceNode == null || !patchSourceNode.has("type")) {
            return;
        }
        if (existing.getSource() == null) {
            return;
        }
        String patchType = patchSourceNode.get("type").asText();
        String existingType = sourceType(existing.getSource());
        if (existingType != null && !existingType.equals(patchType)) {
            throw new ValidationException("Field 'source.type' is immutable and cannot be changed");
        }
    }

    /**
     * Merge patch into a deep copy of existing config. Source fields from the patch JsonNode
     * are applied to the existing polymorphic Source without requiring a type discriminator.
     */
    static ResourceConfig merge(ResourceConfig existing, JsonNode patchNode, ObjectMapper objectMapper) {
        try {
            return doMerge(existing, patchNode, objectMapper);
        } catch (JsonMappingException e) {
            throw new ValidationException("Invalid config patch: " + e.getMessage());
        }
    }

    private static ResourceConfig doMerge(ResourceConfig existing, JsonNode patchNode, ObjectMapper objectMapper)
            throws JsonMappingException {
        ResourceConfig result = DeepClone.of(existing, objectMapper);

        if (patchNode.has("source") && !patchNode.get("source").isNull()) {
            JsonNode sourceNode = patchNode.get("source");
            if (result.getSource() == null) {
                if (!sourceNode.has("type")) {
                    throw new ValidationException("Field 'source.type' is required when creating source");
                }
                result.setSource(objectMapper.convertValue(sourceNode, Source.class));
            } else {
                result.setSource(objectMapper.updateValue(result.getSource(), sourceNode));
            }
        }
        if (patchNode.has("hosting") && !patchNode.get("hosting").isNull()) {
            if (result.getHosting() == null) result.setHosting(new ResourceConfig.HostingConfig());
            result.setHosting(objectMapper.updateValue(result.getHosting(), patchNode.get("hosting")));
        }
        if (patchNode.has("networking") && !patchNode.get("networking").isNull()) {
            if (result.getNetworking() == null) result.setNetworking(new ResourceConfig.NetworkingConfig());
            if (patchNode.get("networking").has("ports")) {
                result.getNetworking()
                        .setPorts(new ArrayList<>(objectMapper
                                .convertValue(patchNode.get("networking"), ResourceConfig.NetworkingConfig.class)
                                .getPorts()));
            }
        }
        if (patchNode.has("healthCheck") && !patchNode.get("healthCheck").isNull()) {
            if (result.getHealthCheck() == null) result.setHealthCheck(new ResourceConfig.HealthCheckConfig());
            result.setHealthCheck(objectMapper.updateValue(result.getHealthCheck(), patchNode.get("healthCheck")));
        }
        if (patchNode.has("env") && !patchNode.get("env").isNull()) {
            JsonNode envNode = patchNode.get("env");
            if (result.getEnv() == null) {
                result.setEnv(new HashMap<>());
            }
            var merged = new HashMap<>(result.getEnv());
            envNode.fields().forEachRemaining(entry -> {
                if (entry.getValue().isNull()) {
                    merged.remove(entry.getKey());
                } else {
                    merged.put(entry.getKey(), entry.getValue().asText());
                }
            });
            result.setEnv(merged);
        }
        if (patchNode.has("runMode") && !patchNode.get("runMode").isNull()) {
            result.setRunMode(RunMode.valueOf(patchNode.get("runMode").asText()));
        }
        return result;
    }

    /** Returns true if hosting has changed between existing and updated config. */
    static boolean hasHostingChange(ResourceConfig existing, ResourceConfig updated) {
        if (updated.getHosting() == null) {
            return false;
        }
        if (existing.getHosting() == null) {
            return true;
        }
        var a = existing.getHosting();
        var b = updated.getHosting();
        return !quantityEquals(a.getCpu(), b.getCpu())
                || !quantityEquals(a.getMemory(), b.getMemory())
                || !Objects.equals(a.getMinReplicas(), b.getMinReplicas())
                || !Objects.equals(a.getMaxReplicas(), b.getMaxReplicas());
    }

    /** Compares two Quantity values numerically rather than by string representation. */
    private static boolean quantityEquals(Quantity a, Quantity b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.getNumericalAmount().compareTo(b.getNumericalAmount()) == 0;
    }

    private static String sourceType(Source src) {
        return switch (src) {
            case DockerSource ds -> ds.getType();
            case GitSource gs -> gs.getType();
            case PromotionSource ps -> ps.getType();
        };
    }
}
