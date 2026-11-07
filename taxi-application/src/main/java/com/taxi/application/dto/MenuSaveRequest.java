package com.taxi.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增/修改菜单请求。
 */
public record MenuSaveRequest(
        Long parentId,

        @Min(value = 1, message = "菜单类型取值 1/2/3")
        @Max(value = 3, message = "菜单类型取值 1/2/3")
        Integer menuType,

        @NotBlank(message = "菜单名称不能为空")
        @Size(max = 64)
        String menuName,

        @Size(max = 255)
        String path,

        @Size(max = 255)
        String component,

        @Pattern(regexp = "^$|^[a-z][a-z0-9]*(:[a-z0-9-]+)*$", message = "权限码格式应为 a:b:c")
        @Size(max = 128)
        String permCode,

        @Size(max = 64)
        String icon,

        Integer sortNo,

        @Min(value = 0, message = "显示标记取值 0/1")
        @Max(value = 1, message = "显示标记取值 0/1")
        Integer visible) {
}
