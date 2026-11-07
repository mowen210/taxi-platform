package com.taxi.application.support;

import com.taxi.application.dto.UserView;
import com.taxi.common.crypto.MaskUtil;
import com.taxi.domain.entity.User;

/**
 * 用户实体到视图的装配。敏感字段在这里统一脱敏，杜绝明文外泄。
 */
public final class UserAssembler {

    private UserAssembler() {
    }

    public static UserView toView(User user) {
        if (user == null) {
            return null;
        }
        return new UserView(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                MaskUtil.maskPhone(user.getPhone()),
                user.getIdCardMask(),
                user.getEmail(),
                user.getFleetId(),
                user.getStatus(),
                user.getLastLoginTime(),
                user.getCreateTime());
    }
}
