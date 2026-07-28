package com.seckill.module.activity.controller;

import com.seckill.common.constant.UserRole;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.activity.model.vo.ActivityVO;
import com.seckill.module.activity.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动 REST 接口。
 */
@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /**
     * 商家提交秒杀活动审核（draft → pending）。
     */
    @PostMapping("/{activityId}/submit")
    public Result<ActivityVO> submitForReview(@PathVariable Long activityId) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.getRole() != UserRole.merchant) {
            throw new BusinessException("仅商家可提交审核");
        }
        ActivityVO vo = activityService.submitForReview(user.getUserId(), activityId);
        return Result.success(vo);
    }

    /**
     * 管理员审核通过秒杀活动（pending → preheating）。
     */
    @PostMapping("/{activityId}/approve")
    public Result<ActivityVO> approveActivity(@PathVariable Long activityId) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.getRole() != UserRole.admin) {
            throw new BusinessException("仅管理员可审核活动");
        }
        ActivityVO vo = activityService.approveActivity(activityId);
        return Result.success(vo);
    }
}
