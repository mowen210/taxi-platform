package com.taxi.api.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解。标注在控制器方法上，由 {@link PermissionAspect} 校验当前登录用户的权限码。
 * <p>多个权限码之间是「全部满足」关系；不写权限码表示只要登录即可访问。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresPerm {

    /** 需要的权限码，如 system:user:list */
    String[] value() default {};
}
