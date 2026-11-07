package com.taxi.domain.repository;

import com.taxi.common.result.PageResult;
import com.taxi.domain.alarm.AlarmMedia;

import java.util.List;

/**
 * 报警媒体仓储。
 */
public interface AlarmMediaRepository {

    Long insert(AlarmMedia media);

    AlarmMedia findById(Long id);

    List<AlarmMedia> findByAlarm(Long alarmId);

    PageResult<AlarmMedia> page(Long fleetId, Integer mediaType, int page, int size);

    void updateUploadStatus(Long id, int status, String fileUrl, Long fileSize);
}
