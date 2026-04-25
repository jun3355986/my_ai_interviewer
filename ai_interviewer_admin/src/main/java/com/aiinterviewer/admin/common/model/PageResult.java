package com.aiinterviewer.admin.common.model;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * Unified pagination payload aligned with the existing backend PageResult.
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long current;
    private Long size;
    private Long total;
    private Long pages;
    private List<T> records;

    public PageResult() {
    }

    public PageResult(Long current, Long size, Long total, List<T> records) {
        this.current = current;
        this.size = size;
        this.total = total;
        this.records = records;
        this.pages = calculatePages(size, total);
    }

    public static <T> PageResult<T> of(Long current, Long size, Long total, List<T> records) {
        return new PageResult<>(current, size, total, records);
    }

    public boolean hasNext() {
        return current != null && pages != null && current < pages;
    }

    public boolean hasPrevious() {
        return current != null && current > 1;
    }

    private long calculatePages(Long size, Long total) {
        if (size == null || size <= 0 || total == null || total <= 0) {
            return 0L;
        }
        return (total + size - 1) / size;
    }
}
