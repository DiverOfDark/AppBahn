package eu.appbahn.platform.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PaginationUtil {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

    private PaginationUtil() {}

    public static Pageable toPageable(Integer page, Integer size, String sort) {
        return toPageable(page, size, sort, DEFAULT_SORT);
    }

    public static Pageable toPageable(Integer page, Integer size, String sort, Sort defaultSort) {
        int p = page != null ? page : DEFAULT_PAGE;
        int s = size != null ? size : DEFAULT_SIZE;
        Sort sortObj = parseSort(sort, defaultSort);
        return PageRequest.of(p, s, sortObj);
    }

    private static Sort parseSort(String sort, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        Sort.Direction direction =
                parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
