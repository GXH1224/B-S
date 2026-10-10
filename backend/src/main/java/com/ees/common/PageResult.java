package com.ees.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页结果。
 */
public class PageResult<T> implements Serializable {

    private List<T> records = new ArrayList<>();
    private long total;
    private int page = 1;
    private int size = 20;
    private int pages;
    private boolean hasNext;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, int page, int size) {
        this.records = records == null ? new ArrayList<>() : records;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
        this.hasNext = page < pages;
    }

    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        return new PageResult<>(records, total, page, size);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(new ArrayList<>(), 0, page, size);
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }
}
