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
}
