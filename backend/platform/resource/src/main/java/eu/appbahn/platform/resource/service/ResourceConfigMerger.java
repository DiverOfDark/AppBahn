package eu.appbahn.platform.resource.service;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.appbahn.platform.common.exception.ValidationException;
import eu.appbahn.shared.crd.*;
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
        String existingType = existing.getSource().getType();
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
        ResourceConfig result = deepCopy(existing);

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
            if (result.getHosting() == null) result.setHosting(new ResourceConfig.Hosting());
            result.setHosting(objectMapper.updateValue(result.getHosting(), patchNode.get("hosting")));
        }
        if (patchNode.has("networking") && !patchNode.get("networking").isNull()) {
            if (result.getNetworking() == null) result.setNetworking(new ResourceConfig.Networking());
            if (patchNode.get("networking").has("ports")) {
                result.getNetworking()
                        .setPorts(new ArrayList<>(objectMapper
                                .convertValue(patchNode.get("networking"), ResourceConfig.Networking.class)
                                .getPorts()));
            }
        }
        if (patchNode.has("healthCheck") && !patchNode.get("healthCheck").isNull()) {
            if (result.getHealthCheck() == null) result.setHealthCheck(new ResourceConfig.HealthCheck());
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
            result.setRunMode(patchNode.get("runMode").asText());
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

    private static ResourceConfig deepCopy(ResourceConfig src) {
        var copy = new ResourceConfig();
        if (src.getSource() != null) {
            copy.setSource(deepCopySource(src.getSource()));
        }
        if (src.getHosting() != null) {
            var h = new ResourceConfig.Hosting();
            h.setCpu(src.getHosting().getCpu());
            h.setMemory(src.getHosting().getMemory());
            h.setMinReplicas(src.getHosting().getMinReplicas());
            h.setMaxReplicas(src.getHosting().getMaxReplicas());
            copy.setHosting(h);
        }
        if (src.getNetworking() != null) {
            var n = new ResourceConfig.Networking();
            if (src.getNetworking().getPorts() != null) {
                n.setPorts(src.getNetworking().getPorts().stream()
                        .map(p -> {
                            var pc = new ResourceConfig.PortConfig();
                            pc.setPort(p.getPort());
                            pc.setExpose(p.getExpose());
                            pc.setDomain(p.getDomain());
                            return pc;
                        })
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
            }
            copy.setNetworking(n);
        }
        if (src.getHealthCheck() != null) {
            copy.setHealthCheck(deepCopyHealthCheck(src.getHealthCheck()));
        }
        if (src.getEnv() != null) {
            copy.setEnv(new HashMap<>(src.getEnv()));
        }
        copy.setRunMode(src.getRunMode());
        return copy;
    }

    private static Source deepCopySource(Source src) {
        if (src instanceof DockerSource ds) {
            var copy = new DockerSource();
            copyBaseFields(src, copy);
            copy.setImage(ds.getImage());
            copy.setTag(ds.getTag());
            copy.setRegistryUrl(ds.getRegistryUrl());
            copy.setCredentialRef(ds.getCredentialRef());
            return copy;
        } else if (src instanceof GitSource gs) {
            var copy = new GitSource();
            copyBaseFields(src, copy);
            copy.setUrl(gs.getUrl());
            copy.setBranch(gs.getBranch());
            copy.setPath(gs.getPath());
            copy.setAuth(gs.getAuth());
            copy.setBuildConfig(gs.getBuildConfig());
            return copy;
        } else if (src instanceof PromotionSource ps) {
            var copy = new PromotionSource();
            copyBaseFields(src, copy);
            copy.setSourceEnvironment(ps.getSourceEnvironment());
            copy.setSourceResource(ps.getSourceResource());
            copy.setAutoPromote(ps.getAutoPromote());
            return copy;
        }
        throw new IllegalStateException("Unknown source type: " + src.getClass());
    }

    private static void copyBaseFields(Source src, Source dest) {
        dest.setType(src.getType());
        dest.setPollInterval(src.getPollInterval());
        dest.setWebhookEnabled(src.getWebhookEnabled());
    }

    private static ResourceConfig.HealthCheck deepCopyHealthCheck(ResourceConfig.HealthCheck src) {
        var hc = new ResourceConfig.HealthCheck();
        hc.setReadiness(deepCopyProbe(src.getReadiness()));
        hc.setLiveness(deepCopyProbe(src.getLiveness()));
        hc.setStartup(deepCopyProbe(src.getStartup()));
        return hc;
    }

    private static ResourceConfig.Probe deepCopyProbe(ResourceConfig.Probe src) {
        if (src == null) return null;
        var p = new ResourceConfig.Probe();
        if (src.getHttpGet() != null) {
            var h = new ResourceConfig.HttpGetAction();
            h.setPath(src.getHttpGet().getPath());
            h.setPort(src.getHttpGet().getPort());
            p.setHttpGet(h);
        }
        if (src.getTcpSocket() != null) {
            var t = new ResourceConfig.TcpSocketAction();
            t.setPort(src.getTcpSocket().getPort());
            p.setTcpSocket(t);
        }
        if (src.getExec() != null) {
            var e = new ResourceConfig.ExecAction();
            e.setCommand(
                    src.getExec().getCommand() != null
                            ? new ArrayList<>(src.getExec().getCommand())
                            : null);
            p.setExec(e);
        }
        p.setInitialDelaySeconds(src.getInitialDelaySeconds());
        p.setPeriodSeconds(src.getPeriodSeconds());
        p.setFailureThreshold(src.getFailureThreshold());
        return p;
    }
}
