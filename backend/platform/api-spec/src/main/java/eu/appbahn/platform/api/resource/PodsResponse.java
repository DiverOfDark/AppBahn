package eu.appbahn.platform.api.resource;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Per-pod snapshot for the Resource Detail Overview's pod table and the Scale modal's
 * baseline. Usage fields ({@code cpu.usedMillicores}, {@code memory.usedBytes}) are
 * populated from metrics-server when present and stay {@code null} otherwise — the
 * pod table renders limits-only in that case. Limits mirror what the Resource's
 * {@code hosting} config currently declares (per replica).
 */
@Data
public class PodsResponse {

    @Valid
    private List<PodInfo> pods = new ArrayList<>();

    @Data
    public static class PodInfo {

        @Nullable
        private String name;

        /** K8s pod phase as reported by the apiserver: {@code Pending|Running|Succeeded|Failed|Unknown}. */
        @Nullable
        private String status;

        @Nullable
        private String node;

        @Valid
        @Nullable
        private PodCpuUsage cpu;

        @Valid
        @Nullable
        private PodMemoryUsage memory;

        /** Seconds since the pod's {@code metadata.creationTimestamp}. */
        @Nullable
        private Long ageSeconds;
    }

    @Data
    public static class PodCpuUsage {

        /** Current usage in millicores (metrics-server). Null when metrics-server is unavailable. */
        @Nullable
        private Long usedMillicores;

        /** Effective container limit in millicores. Sums all containers in the pod. */
        @Nullable
        private Long limitMillicores;
    }

    @Data
    public static class PodMemoryUsage {

        /** Current usage in bytes (metrics-server). Null when metrics-server is unavailable. */
        @Nullable
        private Long usedBytes;

        /** Effective container limit in bytes. Sums all containers in the pod. */
        @Nullable
        private Long limitBytes;
    }
}
