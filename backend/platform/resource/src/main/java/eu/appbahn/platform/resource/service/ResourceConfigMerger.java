package eu.appbahn.platform.resource.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.common.exception.ValidationException;
import eu.appbahn.shared.crd.ResourceConfig;
import eu.appbahn.shared.crd.RunMode;
import eu.appbahn.shared.util.DeepClone;
import io.fabric8.kubernetes.api.model.Quantity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

/** Handles JSON Merge Patch semantics for {@link ResourceConfig}. */
final class ResourceConfigMerger {

    private ResourceConfigMerger() {}

    /**
     * Merge patch into a deep copy of existing config.
     */
    static ResourceConfig merge(ResourceConfig existing, JsonNode patchNode, ObjectMapper objectMapper) {
        try {
            return doMerge(existing, patchNode, objectMapper);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Invalid config patch: " + e.getMessage());
        }
    }

    private static ResourceConfig doMerge(ResourceConfig existing, JsonNode patchNode, ObjectMapper objectMapper)
            throws JsonProcessingException {
        ResourceConfig result = existing != null ? DeepClone.of(existing, objectMapper) : new ResourceConfig();

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
            result.setRunMode(objectMapper.treeToValue(patchNode.get("runMode"), RunMode.class));
        }
        return result;
    }

    /** Returns true if hosting has changed between existing and updated config. */
    static boolean hasHostingChange(ResourceConfig existing, ResourceConfig updated) {
        if (updated == null || updated.getHosting() == null) {
            return false;
        }
        if (existing == null || existing.getHosting() == null) {
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
}
