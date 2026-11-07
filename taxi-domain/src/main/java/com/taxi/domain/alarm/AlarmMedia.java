package com.taxi.domain.alarm;

import java.time.LocalDateTime;

/**
 * 报警媒体（t_alarm_media）读模型。
 */
public record AlarmMedia(
        Long id,
        Long alarmId,
        String alarmNo,
        int mediaType,
        Integer channelNo,
        Integer streamType,
        Long mediaId,
        String fileUrl,
        Long fileSize,
        Integer durationSec,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime snapshotTime,
        int uploadStatus,
        LocalDateTime createTime) {
}
