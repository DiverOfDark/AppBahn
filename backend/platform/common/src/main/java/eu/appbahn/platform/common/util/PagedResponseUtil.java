package eu.appbahn.platform.common.util;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Utility for building paged API response objects from Spring {@link Page} results.
 * Eliminates repetitive setContent/setPage/setSize/setTotalElements/setTotalPages calls.
 */
public final class PagedResponseUtil {

    private PagedResponseUtil() {}

    /**
     * Build a paged response from a Spring Page and a mapping function for content items.
     *
     * @param <E>       entity type in the page
     * @param <A>       API model type for content items
     * @param <R>       response type (e.g. PagedResourceResponse)
     * @param page      the Spring Page result
     * @param mapper    function to convert entities to API models
     * @param response  the response object to populate
     * @param contentSetter setter for the content list
     * @param pageSetter    setter for the page number
     * @param sizeSetter    setter for the page size
     * @param totalElementsSetter setter for total elements
     * @param totalPagesSetter    setter for total pages
     * @return the populated response
     */
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
