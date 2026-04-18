package eu.appbahn.platform.common.util;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Populates a paged response model from a Spring {@link Page}. */
public final class PagedResponseUtil {

    private PagedResponseUtil() {}

    public static <E, A, R> R build(
            Page<E> page,
            Function<E, A> mapper,
            R response,
            java.util.function.BiConsumer<R, List<A>> contentSetter,
            java.util.function.BiConsumer<R, Integer> pageSetter,
            java.util.function.BiConsumer<R, Integer> sizeSetter,
            java.util.function.BiConsumer<R, Long> totalElementsSetter,
            java.util.function.BiConsumer<R, Integer> totalPagesSetter) {
        contentSetter.accept(response, page.getContent().stream().map(mapper).toList());
        pageSetter.accept(response, page.getNumber());
        sizeSetter.accept(response, page.getSize());
        totalElementsSetter.accept(response, page.getTotalElements());
        totalPagesSetter.accept(response, page.getTotalPages());
        return response;
    }
}
