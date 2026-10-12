package com.ees.common;

/**
 * 分页参数统一收敛。
 *
 * <p>背景：各 service 的 {@code PageRequest.of(Math.max(0, page - 1), size, ...)} 只对
 * {@code page} 做了保护，{@code size} 没有任何上限 —— 一个
 * {@code ?size=1000000} 的请求就能把整张表拉进内存，在压测中属于「单请求打爆 JVM」的典型。</p>
 *
 * <p>所有对外列表接口在方法开头调用 {@link #size(int)} 归一化 size 即可，
 * 既限制了数据库实际返回条数，也让 {@code PageResult} 里回给前端的 size 保持一致。</p>
 */
public final class PageLimits {

    /** 单页最大条数。 */
    public static final int MAX_PAGE_SIZE = 200;

    private PageLimits() {
    }

    /** 归一化每页条数：至少 1 条，最多 {@value #MAX_PAGE_SIZE} 条。 */
    public static int size(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
