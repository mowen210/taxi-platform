package com.taxi.api.controller;

import com.taxi.api.security.RequiresPerm;
import com.taxi.application.dto.NoticeView;
import com.taxi.application.service.NoticeAppService;
import com.taxi.common.result.PageResult;
import com.taxi.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知查询接口（站内信、短信发送结果）。
 */
@Tag(name = "通知管理")
@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private final NoticeAppService noticeAppService;

    public NoticeController(NoticeAppService noticeAppService) {
        this.noticeAppService = noticeAppService;
    }

    @Operation(summary = "分页查询通知", description = "noticeType 取值 CERT / ALARM / INVOICE / SYSTEM")
    @GetMapping
    @RequiresPerm("notice:list")
    public R<PageResult<NoticeView>> page(@RequestParam(required = false) String noticeType,
                                          @RequestParam(required = false) Integer sendStatus,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "20") long size) {
        return R.ok(noticeAppService.page(noticeType, sendStatus, page, size));
    }
}
