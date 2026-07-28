package com.seckill.module.user.model.entity;

import com.seckill.common.constant.BanStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SysUserTest {

    private SysUser aUser(BanStatus status) {
        SysUser u = new SysUser();
        u.setUserId(1L);
        u.setBanStatus(status);
        return u;
    }

    @Test
    void ban_normalToBanned() {
        SysUser user = aUser(BanStatus.normal);
        user.ban();
        assertThat(user.getBanStatus()).isEqualTo(BanStatus.banned);
    }

    @Test
    void ban_alreadyBanned_throws() {
        SysUser user = aUser(BanStatus.banned);
        assertThatThrownBy(user::ban)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已被封禁");
    }

    @Test
    void unban_bannedToNormal() {
        SysUser user = aUser(BanStatus.banned);
        user.unban();
        assertThat(user.getBanStatus()).isEqualTo(BanStatus.normal);
    }

    @Test
    void unban_alreadyNormal_throws() {
        SysUser user = aUser(BanStatus.normal);
        assertThatThrownBy(user::unban)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未被封禁");
    }
}
