package com.mca.payroll.dto;

import java.util.List;

/** One page of results (page numbers start at 0). */
public record PageResult<T>(List<T> items, int page, int pageSize, long totalItems) {

    public int totalPages() {
        return pageSize <= 0 ? 0 : (int) ((totalItems + pageSize - 1) / pageSize);
    }

    public boolean hasNext() {
        return page + 1 < totalPages();
    }

    public boolean hasPrevious() {
        return page > 0;
    }
}
