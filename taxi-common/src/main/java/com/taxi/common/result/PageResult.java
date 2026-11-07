package com.taxi.common.result;

import java.io.Serializable;
import java.util.List;

/**
 * 分页返回结构，作为 {@code R.data} 的载荷。
 *
 * @param total 总记录数
 * @param page  当前页码（从 1 开始）
 * @param size  每页条数
 * @param list  当前页数据
 */
public record PageResult<T>(long total, long page, long size, List<T> list) implements Serializable {

    public static <T> PageResult<T> of(long total, long page, long size, List<T> list) {
        return new PageResult<>(total, page, size, list == null ? List.of() : list);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(0L, page, size, List.of());
    }
}
