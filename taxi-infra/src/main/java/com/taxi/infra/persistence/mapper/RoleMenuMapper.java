package com.taxi.infra.persistence.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-菜单关联 Mapper。
 */
public interface RoleMenuMapper {

    @Select("SELECT menu_id FROM t_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIds(@Param("roleId") Long roleId);

    @Delete("DELETE FROM t_role_menu WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Insert("""
            INSERT INTO t_role_menu (id, role_id, menu_id, create_time, create_by)
            VALUES (#{id}, #{roleId}, #{menuId}, NOW(), #{createBy})
            """)
    int insertOne(@Param("id") Long id, @Param("roleId") Long roleId,
                  @Param("menuId") Long menuId, @Param("createBy") Long createBy);
}
