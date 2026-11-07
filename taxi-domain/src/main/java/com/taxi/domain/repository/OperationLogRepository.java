package com.taxi.domain.repository;

import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.OperationLog;

import java.time.LocalDateTime;

/**
 * 操作日志仓储。日志为追加写，不提供更新与删除（按分区保留策略归档）。
 */
public interface OperationLogRepository {

    void save(OperationLog log);

    PageResult<OperationLog> search(String keyword, Long userId, LocalDateTime from, LocalDateTime to, long page, long size);
}
