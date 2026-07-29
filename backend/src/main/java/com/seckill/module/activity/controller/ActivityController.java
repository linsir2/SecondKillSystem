package com.seckill.module.activity.controller;

import com.seckill.common.constant.UserRole;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.PageVO;
import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.activity.model.dto.CreateActivityRequest;
import com.seckill.module.activity.model.vo.ActivityVO;
import com.seckill.module.activity.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * 商家创建秒杀活动草稿。
     */
    @PostMapping
    public Result<ActivityVO> createActivity(@RequestBody CreateActivityRequest request) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.getRole() != UserRole.merchant) {
            throw new BusinessException("仅商家可创建活动");
        }
        ActivityVO vo = activityService.createActivity(user.getUserId(), request);
        return Result.success(vo);
    }

    /**
     * 活动列表（按角色路由）。
     * <ul>
     *   <li>merchant → 自己创建的活动（全状态）</li>
     *   <li>admin → 全部活动（全状态）</li>
     *   <li>user → 可见活动（preheating / running / ended）</li>
     * </ul>
     */
    @GetMapping
    public Result<PageVO<ActivityVO>> listActivities(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        PageVO<ActivityVO> result;
        switch (user.getRole()) {
            case merchant -> result = activityService.listMerchantActivities(user.getUserId(), page, pageSize);
            case admin -> result = activityService.listAllActivities(page, pageSize);
            default -> result = activityService.listActiveActivities(page, pageSize);
        }
        return Result.success(result);
    }

    /**
     * 活动详情（含秒杀商品列表）。
     */
    @GetMapping("/{activityId}")
    public Result<ActivityVO> getActivityDetail(@PathVariable Long activityId) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        ActivityVO vo = activityService.getActivityDetail(activityId);
        return Result.success(vo);
    }
}
