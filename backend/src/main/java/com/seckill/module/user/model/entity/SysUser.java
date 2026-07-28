package com.seckill.module.user.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seckill.common.constant.BanStatus;
import com.seckill.common.constant.UserRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表 sys_user 对应的实体。
 *
 * <p>封禁/解封转换方法内化状态校验。
 * Invalid transition 抛 {@link IllegalStateException}，由 Service catch 转 {@link com.seckill.common.exception.BusinessException}。</p>
 */
@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.ASSIGN_ID)
    private Long userId;
    private String userName;
    private String email;
    private String password;
    private UserRole role;
    private BanStatus banStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ========================================================================
    // 领域行为
    // ========================================================================

    /**
     * 封禁用户（normal → banned）。
     *
     * @throws IllegalStateException 当前已封禁
     */
    public void ban() {
        if (this.banStatus == BanStatus.banned) {
            throw new IllegalStateException("用户已被封禁");
        }
        this.banStatus = BanStatus.banned;
    }

    /**
     * 解封用户（banned → normal）。
     *
     * @throws IllegalStateException 当前未被封禁
     */
    public void unban() {
        if (this.banStatus == BanStatus.normal) {
            throw new IllegalStateException("用户未被封禁");
        }
        this.banStatus = BanStatus.normal;
    }
}
