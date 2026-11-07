package com.taxi.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.OperationLog;
import com.taxi.domain.repository.OperationLogRepository;
import com.taxi.infra.persistence.mapper.OperationLogMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 操作日志仓储实现（只增不改，按时间倒序查询）。
 */
@Repository
public class OperationLogRepositoryImpl implements OperationLogRepository {

    private final OperationLogMapper operationLogMapper;

    public OperationLogRepositoryImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public void save(OperationLog log) {
        operationLogMapper.insert(log);
    }

    @Override
    public PageResult<OperationLog> search(String keyword, Long userId, LocalDateTime from, LocalDateTime to,
                                           long page, long size) {
        LambdaQueryWrapper<OperationLog> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(OperationLog::getUserName, trimmed)
                    .or().like(OperationLog::getModule, trimmed)
                    .or().like(OperationLog::getAction, trimmed)
                    .or().like(OperationLog::getTraceId, trimmed));
        }
        if (userId != null) {
            wrapper.eq(OperationLog::getUserId, userId);
        }
        if (from != null) {
            wrapper.ge(OperationLog::getOpTime, from);
        }
        if (to != null) {
            wrapper.le(OperationLog::getOpTime, to);
        }
        wrapper.orderByDesc(OperationLog::getOpTime);
        Page<OperationLog> result = operationLogMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }
}
