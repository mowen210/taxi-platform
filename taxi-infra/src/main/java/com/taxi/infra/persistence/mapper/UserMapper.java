package com.taxi.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taxi.domain.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper。仅仓储层可调用，避免绕过加解密逻辑。
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 查询用户全部权限码（角色 -> 菜单 perm_code 去重）。
     */
    @Select("""
            SELECT DISTINCT m.perm_code
            FROM t_menu m
                     JOIN t_role_menu rm ON rm.menu_id = m.id
                     JOIN t_user_role ur ON ur.role_id = rm.role_id
            WHERE ur.user_id = #{userId}
              AND m.deleted = 0
              AND m.perm_code IS NOT NULL
              AND m.perm_code <> ''
            """)
    List<String> selectPermissionCodes(@Param("userId") Long userId);

    /**
     * 用户角色中最宽的数据权限：data_scope 取最小值（1 全部 &gt; 2 车队 &gt; 3 本人）。
     */
    @Select("""
            SELECT MIN(r.data_scope)
            FROM t_role r
                     JOIN t_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.deleted = 0
            """)
    Integer selectMaxDataScope(@Param("userId") Long userId);
}
