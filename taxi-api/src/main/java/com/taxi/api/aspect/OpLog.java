package com.taxi.api.aspect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。标注在需要留痕的写接口上，由 {@link OperationLogAspect} 落 t_operation_log。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OpLog {

    /** 模块名，如「系统管理」 */
    String module();

    /** 动作，如「新增用户」 */
    String action();

    /** 业务类型，用于按业务检索日志 */
    String bizType() default "";
}
