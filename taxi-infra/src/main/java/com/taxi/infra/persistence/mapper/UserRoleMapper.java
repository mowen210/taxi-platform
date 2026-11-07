package com.taxi.infra.persistence.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色关联 Mapper（关联表无软删除，物理覆盖）。
 */
public interface UserRoleMapper {

    @Select("SELECT role_id FROM t_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIds(@Param("userId") Long userId);

    @Delete("DELETE FROM t_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO t_user_role (id, user_id, role_id, create_time, create_by)
            VALUES (#{id}, #{userId}, #{roleId}, NOW(), #{createBy})
            """)
    int insertOne(@Param("id") Long id, @Param("userId") Long userId,
                  @Param("roleId") Long roleId, @Param("createBy") Long createBy);

    @Select("SELECT COUNT(1) FROM t_user_role WHERE role_id = #{roleId}")
    long countByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT user_id FROM t_user_role WHERE role_id = #{roleId}")
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);
}
