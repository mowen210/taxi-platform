package com.taxi.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taxi.domain.entity.OperationLog;

/**
 * 操作日志 Mapper（只增不改）。
 */
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
