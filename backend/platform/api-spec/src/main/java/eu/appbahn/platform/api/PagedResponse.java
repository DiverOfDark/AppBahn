package eu.appbahn.platform.api;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class PagedResponse {

    @Nullable
    private Integer page;

    @Nullable
    private Integer size;

    @Nullable
    private Long totalElements;

    @Nullable
    private Integer totalPages;
}
