package eu.appbahn.shared.tunnel;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FullSyncPayload(String clusterName, List<ResourceSyncPayload> resources) {}
