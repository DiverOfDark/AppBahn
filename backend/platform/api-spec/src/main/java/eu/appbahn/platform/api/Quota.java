package eu.appbahn.platform.api;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class Quota {

    @Nullable
    private Double maxCpuCores;

    @Nullable
    private Integer maxMemoryMb;

    @Nullable
    private Integer maxStorageGb;

    @Nullable
    private Integer maxResources;
}
