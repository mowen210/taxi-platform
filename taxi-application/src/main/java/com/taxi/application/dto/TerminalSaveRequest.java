package com.taxi.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增/修改终端请求。
 */
public record TerminalSaveRequest(
        @NotBlank(message = "终端手机号不能为空")
        @Pattern(regexp = "^\\d{7,20}$", message = "终端手机号需为 7~20 位数字")
        String terminalId,

        @Size(max = 64, message = "终端编号长度不能超过 64")
        String terminalCode,

        @Size(max = 32, message = "SIM 卡号长度不能超过 32")
        String simNo,

        @Size(max = 16, message = "厂商 ID 长度不能超过 16")
        String manufacturerId,

        @Size(max = 64, message = "终端型号长度不能超过 64")
        String terminalModel,

        @Size(max = 64, message = "终端序列号长度不能超过 64")
        String terminalSn,

        @Pattern(regexp = "^$|^(2011|2019)$", message = "协议版本取值 2011 或 2019")
        String protocolVersion,

        @Size(max = 64, message = "鉴权码长度不能超过 64")
        String authCode,

        @Size(max = 32, message = "固件版本长度不能超过 32")
        String firmwareVersion) {
}
