package com.taxi.application.dto;

import java.time.LocalDateTime;

/**
 * 通知视图。
 */
public record NoticeView(
        Long id,
        String noticeType,
        String channel,
        String receiver,
        String bizType,
        String bizId,
        String title,
        String content,
        Integer sendStatus,
        String failMsg,
        LocalDateTime sendTime,
        LocalDateTime createTime) {
}
