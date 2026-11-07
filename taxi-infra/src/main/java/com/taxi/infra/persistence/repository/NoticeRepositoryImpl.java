package com.taxi.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Notice;
import com.taxi.domain.repository.NoticeRepository;
import com.taxi.infra.persistence.mapper.NoticeMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 通知仓储实现。
 */
@Repository
public class NoticeRepositoryImpl implements NoticeRepository {

    private final NoticeMapper noticeMapper;

    public NoticeRepositoryImpl(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    @Override
    public void save(Notice notice) {
        noticeMapper.insert(notice);
    }

    @Override
    public void update(Notice notice) {
        noticeMapper.updateById(notice);
    }

    @Override
    public PageResult<Notice> search(String noticeType, Integer sendStatus, int page, int size) {
        LambdaQueryWrapper<Notice> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(noticeType)) {
            wrapper.eq(Notice::getNoticeType, noticeType.trim());
        }
        if (sendStatus != null) {
            wrapper.eq(Notice::getSendStatus, sendStatus);
        }
        wrapper.orderByDesc(Notice::getCreateTime);
        Page<Notice> result = noticeMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public boolean existsSince(String noticeType, String bizType, String bizId, java.time.LocalDateTime since) {
        return noticeMapper.selectCount(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getNoticeType, noticeType)
                .eq(Notice::getBizType, bizType)
                .eq(Notice::getBizId, bizId)
                .ge(Notice::getCreateTime, since)) > 0;
    }
}
