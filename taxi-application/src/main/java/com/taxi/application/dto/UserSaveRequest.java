package com.taxi.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新增/修改用户请求。
 * <p>口令只在新增时使用；修改口令走独立接口，避免与资料更新混在一起。</p>
 */
public record UserSaveRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 64, message = "用户名长度需在 4~64 之间")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "用户名只允许字母、数字、下划线、点、横线")
        String username,

        @Size(min = 8, max = 64, message = "口令长度需在 8~64 之间")
        String password,

        @Size(max = 64, message = "姓名长度不能超过 64")
        String realName,

        @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
        String phone,

        @Pattern(regexp = "^$|^\\d{17}[\\dXx]$", message = "身份证号格式不正确")
        String idCard,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128)
        String email,

        Long fleetId,

        @Min(value = 0, message = "状态取值 0 或 1")
        @Max(value = 1, message = "状态取值 0 或 1")
        Integer status,

        @Size(max = 255, message = "备注长度不能超过 255")
        String remark,

        List<Long> roleIds) {
}
