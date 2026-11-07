package com.taxi.application.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 报警处置请求。action：ACK 受理 / HANDLE 处置完成 / IGNORE 忽略。
 */
public record AlarmHandleRequest(
        @NotBlank(message = "处置动作不能为空") String action,
        String remark) {
}
