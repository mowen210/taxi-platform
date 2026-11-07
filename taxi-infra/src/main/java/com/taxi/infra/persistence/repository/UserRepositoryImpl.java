package com.taxi.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taxi.common.crypto.AesGcmCipher;
import com.taxi.common.crypto.HmacUtil;
import com.taxi.common.crypto.MaskUtil;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.User;
import com.taxi.domain.repository.UserRepository;
import com.taxi.infra.persistence.mapper.UserMapper;
import com.taxi.infra.persistence.mapper.UserRoleMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 用户仓储实现。
 * <p><b>加解密边界</b>：写库前对手机号/身份证做 AES-GCM 加密（作用于副本，不改调用方对象）；
 * 读库后解密为明文，因此仓储返回的实体中敏感字段为明文，仅限本进程内存使用，禁止直接序列化出接口。</p>
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private static final Pattern MOBILE = Pattern.compile("^1\\d{10}$");

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final String hmacSecret;

    public UserRepositoryImpl(UserMapper userMapper,
                              UserRoleMapper userRoleMapper,
                              @Value("${security.hmac-secret}") String hmacSecret) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.hmacSecret = hmacSecret;
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(decrypt(userMapper.selectById(id)));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = Wrappers.<User>lambdaQuery().eq(User::getUsername, username);
        return Optional.ofNullable(decrypt(userMapper.selectOne(wrapper)));
    }

    @Override
    public boolean existsByUsername(String username, Long excludeId) {
        LambdaQueryWrapper<User> wrapper = Wrappers.<User>lambdaQuery().eq(User::getUsername, username);
        if (excludeId != null) {
            wrapper.ne(User::getId, excludeId);
        }
        return userMapper.selectCount(wrapper) > 0;
    }

    @Override
    public PageResult<User> search(String keyword, Long fleetId, Integer status, long page, long size) {
        LambdaQueryWrapper<User> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            String phoneHash = MOBILE.matcher(trimmed).matches() ? HmacUtil.sha256Hex(trimmed, hmacSecret) : null;
            wrapper.and(w -> {
                w.like(User::getUsername, trimmed).or().like(User::getRealName, trimmed);
                if (phoneHash != null) {
                    w.or().eq(User::getPhoneHash, phoneHash);
                }
            });
        }
        if (fleetId != null) {
            wrapper.eq(User::getFleetId, fleetId);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreateTime);

        Page<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::decrypt);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public void save(User user) {
        User persist = toPersist(user);
        userMapper.insert(persist);
        // 雪花 ID 由 MyBatis-Plus 写入持久化副本，必须回填给调用方，否则后续关联表拿不到用户 ID
        user.setId(persist.getId());
    }

    @Override
    public void update(User user) {
        userMapper.updateById(toPersist(user));
    }

    @Override
    public void deleteById(Long id) {
        userMapper.deleteById(id);
    }

    @Override
    public void updatePassword(Long id, String encodedPassword) {
        LambdaUpdateWrapper<User> wrapper = Wrappers.<User>lambdaUpdate()
                .eq(User::getId, id)
                .set(User::getPassword, encodedPassword)
                .set(User::getUpdateTime, LocalDateTime.now());
        userMapper.update(null, wrapper);
    }

    @Override
    public void updateLastLogin(Long id, LocalDateTime loginTime) {
        LambdaUpdateWrapper<User> wrapper = Wrappers.<User>lambdaUpdate()
                .eq(User::getId, id)
                .set(User::getLastLoginTime, loginTime)
                .set(User::getUpdateTime, LocalDateTime.now());
        userMapper.update(null, wrapper);
    }

    @Override
    public List<Long> findRoleIds(Long userId) {
        return userRoleMapper.selectRoleIds(userId);
    }

    @Override
    public void replaceRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        if (roleIds == null) {
            return;
        }
        for (Long roleId : roleIds) {
            if (roleId != null) {
                userRoleMapper.insertOne(IdWorker.getId(), userId, roleId, null);
            }
        }
    }

    @Override
    public List<String> findPermissionCodes(Long userId) {
        return userMapper.selectPermissionCodes(userId);
    }

    @Override
    public Integer findMaxDataScope(Long userId) {
        Integer scope = userMapper.selectMaxDataScope(userId);
        return scope == null ? 3 : scope;
    }

    @Override
    public long countByFleetId(Long fleetId) {
        return userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getFleetId, fleetId));
    }

    @Override
    public List<Long> findUserIdsByRole(Long roleId) {
        return userRoleMapper.selectUserIdsByRoleId(roleId);
    }

    /** 解密敏感字段，原地生效（仅内存） */
    private User decrypt(User user) {
        if (user == null) {
            return null;
        }
        user.setPhone(AesGcmCipher.decrypt(user.getPhone()));
        user.setIdCard(AesGcmCipher.decrypt(user.getIdCard()));
        return user;
    }

    /** 生成写库副本，避免把明文与密文混在同一对象上 */
    private User toPersist(User source) {
        User target = new User();
        BeanUtils.copyProperties(source, target);
        if (StringUtils.hasText(source.getPhone())) {
            if (source.getPhone().contains("*")) {
                // 正常：前端回传的是脱敏值，视为「不修改」；置空让 updateById 跳过该列，避免把掩码写进密文列
                target.setPhone(null);
                target.setPhoneHash(null);
            } else {
                String plainPhone = source.getPhone().trim();
                target.setPhone(AesGcmCipher.encrypt(plainPhone));
                target.setPhoneHash(HmacUtil.sha256Hex(plainPhone, hmacSecret));
            }
        }
        if (StringUtils.hasText(source.getIdCard())) {
            if (source.getIdCard().contains("*")) {
                target.setIdCard(null);
                target.setIdCardMask(null);
            } else {
                String plainIdCard = source.getIdCard().trim();
                target.setIdCard(AesGcmCipher.encrypt(plainIdCard));
                target.setIdCardMask(MaskUtil.maskIdCard(plainIdCard));
            }
        }
        return target;
    }
}
