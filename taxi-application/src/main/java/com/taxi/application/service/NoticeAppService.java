package com.taxi.application.service;

import com.taxi.application.dto.NoticeView;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Notice;
import com.taxi.domain.repository.NoticeRepository;
import org.springframework.stereotype.Service;

/**
 * 通知查询用例。
 */
@Service
public class NoticeAppService {

    private final NoticeRepository noticeRepository;

    public NoticeAppService(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

    public PageResult<NoticeView> page(String noticeType, Integer sendStatus, long page, long size) {
        long safePage = Math.max(1L, page);
        long safeSize = Math.min(Math.max(1L, size), 100L);
        PageResult<Notice> result = noticeRepository.search(noticeType, sendStatus, (int) safePage, (int) safeSize);
        return PageResult.of(result.total(), result.page(), result.size(),
                result.list().stream().map(n -> new NoticeView(n.getId(), n.getNoticeType(), n.getChannel(),
                        n.getReceiver(), n.getBizType(), n.getBizId(), n.getTitle(), n.getContent(),
                        n.getSendStatus(), n.getFailMsg(), n.getSendTime(), n.getCreateTime())).toList());
    }
}
