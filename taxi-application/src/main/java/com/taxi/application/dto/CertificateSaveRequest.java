package com.taxi.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 新增/修改司机证件请求。
 */
public record CertificateSaveRequest(
        @NotNull(message = "证件类型不能为空")
        @Min(value = 1, message = "证件类型取值 1~9")
        @Max(value = 9, message = "证件类型取值 1~9")
        Integer certType,

        @Size(max = 64, message = "证件号长度不能超过 64")
        String certNo,

        LocalDate issueDate,

        @NotNull(message = "到期日期不能为空")
        LocalDate expireDate,

        @Size(max = 512, message = "影像地址长度不能超过 512")
        String fileUrl) {
}
