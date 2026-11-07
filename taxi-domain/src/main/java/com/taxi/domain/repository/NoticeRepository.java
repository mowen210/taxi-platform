package com.taxi.domain.repository;

import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Notice;

/**
 * 消息通知仓储。
 */
public interface NoticeRepository {

    void save(Notice notice);

    void update(Notice notice);

    PageResult<Notice> search(String noticeType, Integer sendStatus, int page, int size);

    /** 指定时间之后是否已存在同一业务对象的通知（用于定时任务每日去重） */
    boolean existsSince(String noticeType, String bizType, String bizId, java.time.LocalDateTime since);
}
