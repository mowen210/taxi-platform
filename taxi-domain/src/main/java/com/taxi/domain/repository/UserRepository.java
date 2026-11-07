package com.taxi.domain.repository;

import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户仓储。实现位于 taxi-infra，领域层不感知 MyBatis。
 */
public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username, Long excludeId);

    /**
     * 分页查询。手机号检索由仓储层内部换算 HMAC 摘要完成，调用方只传明文关键字。
     *
     * @param keyword 登录名/姓名/完整手机号，可为空
     * @param fleetId 车队过滤，为空表示不限
     * @param status  状态过滤，可为空
     */
    PageResult<User> search(String keyword, Long fleetId, Integer status, long page, long size);

    void save(User user);

    void update(User user);

    void deleteById(Long id);

    void updatePassword(Long id, String encodedPassword);

    void updateLastLogin(Long id, LocalDateTime loginTime);

    List<Long> findRoleIds(Long userId);

    /** 全量覆盖用户角色，事务由应用层保证 */
    void replaceRoles(Long userId, List<Long> roleIds);

    /** 用户拥有的权限码（角色 -> 菜单 perm_code 去重） */
    List<String> findPermissionCodes(Long userId);

    /** 用户角色中最宽的数据权限：1 全部 2 本车队 3 本人 */
    Integer findMaxDataScope(Long userId);

    /** 统计某车队下的用户数，删除车队前校验 */
    long countByFleetId(Long fleetId);

    /** 某角色下所有用户 ID，用于角色变更后批量失效权限缓存 */
    List<Long> findUserIdsByRole(Long roleId);
}
