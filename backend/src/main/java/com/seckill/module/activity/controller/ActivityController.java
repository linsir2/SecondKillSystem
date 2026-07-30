package com.seckill.module.activity.controller;

import com.seckill.common.constant.UserRole;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.PageVO;
import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.activity.model.dto.CreateActivityRequest;
import com.seckill.module.activity.model.dto.RejectActivityRequest;
import com.seckill.module.activity.model.vo.ActivityVO;
import com.seckill.module.activity.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
@Tag(name = "活动管理", description = "秒杀活动 CRUD、提交审核、审核通过/驳回")
@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @Operation(summary = "提交审核", description = "商家将活动草稿提交为待审核状态（draft → pending）")
    @PreAuthorize("hasRole('merchant')")
    @PostMapping("/{activityId}/submit")
    public Result<ActivityVO> submitForReview(@Parameter(description = "活动ID") @PathVariable Long activityId) {
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

    @Operation(summary = "审核通过", description = "管理员审核通过活动，进入预热阶段（pending → preheating）")
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/{activityId}/approve")
    public Result<ActivityVO> approveActivity(@Parameter(description = "活动ID") @PathVariable Long activityId) {
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

    @Operation(summary = "驳回活动", description = "管理员驳回活动申请，退回草稿状态并记录驳回理由（pending → draft）")
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/{activityId}/reject")
    public Result<ActivityVO> rejectActivity(@Parameter(description = "活动ID") @PathVariable Long activityId,
                                              @Valid @RequestBody RejectActivityRequest request) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.getRole() != UserRole.admin) {
            throw new BusinessException("仅管理员可驳回活动");
        }
        ActivityVO vo = activityService.rejectActivity(activityId, request.reason());
        return Result.success(vo);
    }

    @Operation(summary = "创建活动", description = "商家创建秒杀活动草稿，同时绑定秒杀商品")
    @PreAuthorize("hasRole('merchant')")
    @PostMapping
    public Result<ActivityVO> createActivity(@Valid @RequestBody CreateActivityRequest request) {
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

    @Operation(summary = "活动列表", description = "按角色路由查看活动列表 —— 商家看自己的、管理员看全部、用户看可见活动（preheating/running/ended）")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Result<PageVO<ActivityVO>> listActivities(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
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

    @Operation(summary = "活动详情", description = "查看活动详情，含秒杀商品列表")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{activityId}")
    public Result<ActivityVO> getActivityDetail(@Parameter(description = "活动ID") @PathVariable Long activityId) {
        CurrentUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        ActivityVO vo = activityService.getActivityDetail(activityId);
        return Result.success(vo);
    }
}
