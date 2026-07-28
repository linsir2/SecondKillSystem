package com.seckill.module.user.service;

import com.seckill.module.user.model.dto.UserInfo;

import java.util.List;

/**
 * 用户上下文对外暴露的服务接口（Open Host Service 模式）。
 * <p>下游上下文（Activity / Goods / Order / Seckill / Gateway）通过此接口
 * 查询用户基本信息和角色。认证（JWT/登录）是 user 模块内部实现，不在此接口中。</p>
 */
public interface UserService {

    /**
     * 查询单个用户信息。
     *
     * @param userId 用户 ID
     * @return 用户信息，不存在时返回 {@code null}
     * @throws IllegalArgumentException userId 为 {@code null}
     */
    UserInfo getUserInfo(Long userId);

    /**
     * 批量查询用户信息。
     * <p>返回顺序与输入一致，不存在的用户对应位置为 {@code null}。</p>
     *
     * @param userIds 用户 ID 列表
     * @return 用户信息列表，非 null
     * @throws IllegalArgumentException userIds 为 {@code null}
     */
    List<UserInfo> getUserInfoList(List<Long> userIds);

    /**
     * 封禁用户（仅管理员）。
     *
     * @param operatorId 操作者 ID（必须是 admin）
     * @param userId     目标用户 ID
     * @throws BusinessException 无权操作/用户不存在/已封禁
     */
    void banUser(Long operatorId, Long userId);

    /**
     * 解封用户（仅管理员）。
     *
     * @param operatorId 操作者 ID（必须是 admin）
     * @param userId     目标用户 ID
     * @throws BusinessException 无权操作/用户不存在/未封禁
     */
    void unbanUser(Long operatorId, Long userId);
}
