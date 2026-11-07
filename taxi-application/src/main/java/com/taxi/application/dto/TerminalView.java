package com.taxi.application.dto;

import java.time.LocalDateTime;

/**
 * 终端视图。boundVehicle* 为当前生效绑定，未绑定时为 null。
 */
public record TerminalView(
        Long id,
        String terminalId,
        String terminalCode,
        String simNo,
        String manufacturerId,
        String terminalModel,
        String terminalSn,
        String protocolVersion,
        String firmwareVersion,
        Integer onlineStatus,
        LocalDateTime lastHeartbeatTime,
        LocalDateTime lastOnlineTime,
        LocalDateTime registerTime,
        Long boundVehicleId,
        String boundPlateNo,
        String boundTaximeterSn,
        LocalDateTime createTime) {
}
