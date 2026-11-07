package com.taxi.application.service;

import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.OperationLog;
import com.taxi.domain.repository.OperationLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 操作日志查询用例（只读）。
 */
@Service
public class OperationLogAppService {

    private final OperationLogRepository operationLogRepository;

    public OperationLogAppService(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    public PageResult<OperationLog> page(String keyword, Long userId, LocalDateTime from, LocalDateTime to,
                                         long page, long size) {
        return operationLogRepository.search(keyword, userId, from, to,
                Math.max(1L, page), Math.min(Math.max(1L, size), 100L));
    }
}
