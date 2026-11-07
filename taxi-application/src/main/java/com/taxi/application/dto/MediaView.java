package com.taxi.application.dto;

import java.time.LocalDateTime;

/**
 * 报警媒体视图。
 */
public record MediaView(
        Long id,
        Long alarmId,
        String alarmNo,
        String plateNo,
        int mediaType,
        String mediaTypeDesc,
        Integer channelNo,
        Integer streamType,
        Long mediaId,
        String fileUrl,
        Long fileSize,
        LocalDateTime snapshotTime,
        int uploadStatus,
        String uploadStatusDesc) {
}
