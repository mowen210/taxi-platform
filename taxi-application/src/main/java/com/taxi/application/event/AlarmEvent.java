package com.taxi.application.event;

import com.taxi.application.dto.AlarmView;

/**
 * 报警创建/升级事件，由 WebSocket 广播器消费。
 */
public record AlarmEvent(AlarmView alarm, boolean escalated) {
}
