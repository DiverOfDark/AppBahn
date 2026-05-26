package eu.appbahn.platform.api.tunnel;

import lombok.Data;
import org.springframework.lang.Nullable;

/** One pod's worth of data inside {@link ListPodsResult}. */
@Data
public class ListPodsResultEntry {

    @Nullable
    private String name;

    @Nullable
    private String status;

    @Nullable
    private String node;

    @Nullable
    private Long cpuUsedMillicores;

    @Nullable
    private Long cpuLimitMillicores;

    @Nullable
    private Long memoryUsedBytes;

    @Nullable
    private Long memoryLimitBytes;

    @Nullable
    private Long ageSeconds;
}
