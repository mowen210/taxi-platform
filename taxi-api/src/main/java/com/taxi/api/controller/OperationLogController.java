package com.taxi.api.controller;

import com.taxi.api.security.RequiresPerm;
import com.taxi.application.service.OperationLogAppService;
import com.taxi.common.result.PageResult;
import com.taxi.common.result.R;
import com.taxi.domain.entity.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 操作日志查询接口（只读）。
 */
@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/v1/logs")
public class OperationLogController {

    private final OperationLogAppService operationLogAppService;

    public OperationLogController(OperationLogAppService operationLogAppService) {
        this.operationLogAppService = operationLogAppService;
    }

    @Operation(summary = "分页查询操作日志", description = "支持按关键字、操作人、时间范围过滤")
    @GetMapping
    @RequiresPerm("system:log:list")
    public R<PageResult<OperationLog>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(operationLogAppService.page(keyword, userId, from, to, page, size));
    }
}
