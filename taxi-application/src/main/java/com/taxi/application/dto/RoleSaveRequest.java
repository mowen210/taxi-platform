package com.taxi.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增/修改角色请求。
 */
public record RoleSaveRequest(
        @NotBlank(message = "角色编码不能为空")
        @Size(max = 64)
        String roleCode,

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64)
        String roleName,

        @Min(value = 1, message = "数据权限取值 1/2/3")
        @Max(value = 3, message = "数据权限取值 1/2/3")
        Integer dataScope,

        @Size(max = 255)
        String remark) {
}
