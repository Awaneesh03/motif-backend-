package com.motif.ideaforge.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Generic paginated response wrapper.
 *
 * @param <T> item type
 */
@Data
@Builder
public class PagedResponse<T> {

    private List<T> items;

    /** Total matching rows across all pages. */
    private long totalCount;

    /** Zero-based current page index. */
    private int page;

    /** Requested page size. */
    private int size;

    /** Total number of pages (ceil(totalCount / size)). */
    private int totalPages;

    public static <T> PagedResponse<T> of(List<T> items, long totalCount, int page, int size) {
        int totalPages = size <= 0 ? 1 : (int) Math.ceil((double) totalCount / size);
        return PagedResponse.<T>builder()
                .items(items)
                .totalCount(totalCount)
                .page(page)
                .size(size)
                .totalPages(totalPages)
                .build();
    }
}
