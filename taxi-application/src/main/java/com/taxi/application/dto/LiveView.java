package com.taxi.application.dto;

/**
 * 1078 实时预览结果：HLS 地址由 MediaMTX 提供。
 */
public record LiveView(Long alarmId, String terminalId, int channel, int streamType,
                       boolean requested, String hlsUrl) {
}
