package com.seckill.module.activity.controller;

import com.seckill.common.constant.UserRole;
import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.PageVO;
import com.seckill.common.result.Result;
import com.seckill.module.activity.model.dto.CreateActivityRequest;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.activity.model.vo.ActivityVO;
import com.seckill.module.activity.service.ActivityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityControllerTest {

    @Mock
    private ActivityService activityService;

    @InjectMocks
    private ActivityController controller;

    private final CurrentUser merchant  = new CurrentUser(1L, "商家A",  UserRole.merchant);
    private final CurrentUser user      = new CurrentUser(2L, "用户X",  UserRole.user);
    private final CurrentUser admin     = new CurrentUser(3L, "管理员", UserRole.admin);

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    /** 构建一个用于 mock 返回的 ActivityVO，仅关注可断言的字段 */
    private ActivityVO mockActivityVO(Long activityId, String status) {
        return new ActivityVO(activityId, "活动名", 1L, status,
                null, null, null, null, null);
    }

    // ========================================================================
    // submitForReview — 权限校验
    // ========================================================================

    @Nested
    class SubmitForReview_Auth {

        @Test
        void merchant_canSubmit() {
            SecurityContext.set(merchant);
            when(activityService.submitForReview(1L, 10L))
                    .thenReturn(mockActivityVO(10L, "pending"));

            Result<ActivityVO> result = controller.submitForReview(10L);

            // Result 格式
            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getMessage()).isEqualTo("success");
            assertThat(result.getData()).isNotNull();
            assertThat(result.getErrors()).isNull();

            // 数据内容
            assertThat(result.getData().getActivityId()).isEqualTo(10L);
            assertThat(result.getData().getStatus()).isEqualTo("pending");
        }

        @Test
        void noAuth_throws() {
            assertThatThrownBy(() -> controller.submitForReview(10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("未登录");
        }

        @Test
        void normalUser_throws() {
            SecurityContext.set(user);

            assertThatThrownBy(() -> controller.submitForReview(10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("仅商家可提交审核");
        }

        @Test
        void admin_throws() {
            SecurityContext.set(admin);

            assertThatThrownBy(() -> controller.submitForReview(10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("仅商家可提交审核");
        }
    }

    // ========================================================================
    // submitForReview — 参数传递到 Service
    // ========================================================================

    @Nested
    class SubmitForReview_ParameterPassing {

        /** merchantId = SecurityContext.get().getUserId() */
        @Test
        void merchantId_fromSecurityContext() {
            SecurityContext.set(new CurrentUser(5L, "商家B", UserRole.merchant));
            when(activityService.submitForReview(5L, 10L))
                    .thenReturn(mockActivityVO(10L, "pending"));

            controller.submitForReview(10L);

            verify(activityService).submitForReview(5L, 10L);
        }

        /** activityId = @PathVariable */
        @Test
        void activityId_fromPathVariable() {
            SecurityContext.set(merchant);
            when(activityService.submitForReview(1L, 99L))
                    .thenReturn(mockActivityVO(99L, "pending"));

            controller.submitForReview(99L);

            verify(activityService).submitForReview(1L, 99L);
        }

        /** 验证 merchantId 不会被 activityId 污染 */
        @Test
        void merchantId_and_activityId_independent() {
            SecurityContext.set(new CurrentUser(7L, "商家C", UserRole.merchant));
            when(activityService.submitForReview(7L, 42L))
                    .thenReturn(mockActivityVO(42L, "pending"));

            controller.submitForReview(42L);

            verify(activityService).submitForReview(7L, 42L);
        }
    }

    // ========================================================================
    // submitForReview — 异常传播
    // ========================================================================

    @Nested
    class SubmitForReview_ExceptionPropagation {

        @Test
        void businessExceptionWithoutErrors_propagates() {
            SecurityContext.set(merchant);
            when(activityService.submitForReview(1L, 10L))
                    .thenThrow(new BusinessException("活动不存在"));

            assertThatThrownBy(() -> controller.submitForReview(10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("活动不存在")
                    .satisfies(e ->
                            assertThat(((BusinessException) e).getErrors()).isEmpty());
        }

        @Test
        void businessExceptionWithErrors_propagates() {
            SecurityContext.set(merchant);
            var errors = List.of("商品 ID 999 不存在或不属于您", "《xxx》秒杀库存(999)超过日常库存(100)");
            when(activityService.submitForReview(1L, 10L))
                    .thenThrow(new BusinessException("部分商品校验未通过", errors));

            assertThatThrownBy(() -> controller.submitForReview(10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("部分商品校验未通过")
                    .satisfies(e -> {
                        var be = (BusinessException) e;
                        assertThat(be.getErrors()).hasSize(2);
                        assertThat(be.getErrors().get(0)).contains("999");
                        assertThat(be.getErrors().get(1)).contains("秒杀库存");
                    });
        }

        /** RuntimeException（如 DB 断连）也直接外抛 */
        @Test
        void runtimeException_propagates() {
            SecurityContext.set(merchant);
            when(activityService.submitForReview(1L, 10L))
                    .thenThrow(new RuntimeException("数据库连接超时"));

            assertThatThrownBy(() -> controller.submitForReview(10L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("数据库连接超时");
        }
    }

    // ========================================================================
    // approveActivity — 权限校验
    // ========================================================================

    @Nested
    class ApproveActivity_Auth {

        @Test
        void admin_canApprove() {
            SecurityContext.set(admin);
            when(activityService.approveActivity(10L))
                    .thenReturn(mockActivityVO(10L, "preheating"));

            Result<ActivityVO> result = controller.approveActivity(10L);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData().getStatus()).isEqualTo("preheating");
        }

        @Test
        void noAuth_throws() {
            assertThatThrownBy(() -> controller.approveActivity(10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("未登录");
        }

        @Test
        void merchant_throws() {
            SecurityContext.set(merchant);

            assertThatThrownBy(() -> controller.approveActivity(10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("仅管理员可审核活动");
        }

        @Test
        void user_throws() {
            SecurityContext.set(user);

            assertThatThrownBy(() -> controller.approveActivity(10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("仅管理员可审核活动");
        }
    }

    // ========================================================================
    // approveActivity — 参数传递
    // ========================================================================

    @Nested
    class ApproveActivity_ParameterPassing {

        @Test
        void activityId_fromPathVariable() {
            SecurityContext.set(admin);
            when(activityService.approveActivity(99L))
                    .thenReturn(mockActivityVO(99L, "preheating"));

            controller.approveActivity(99L);

            verify(activityService).approveActivity(99L);
        }
    }

    // ========================================================================
    // approveActivity — 异常传播
    // ========================================================================

    @Nested
    class ApproveActivity_ExceptionPropagation {

        @Test
        void businessException_propagates() {
            SecurityContext.set(admin);
            when(activityService.approveActivity(10L))
                    .thenThrow(new BusinessException("当前状态不可审核"));

            assertThatThrownBy(() -> controller.approveActivity(10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("当前状态不可审核");
        }

        @Test
        void runtimeException_propagates() {
            SecurityContext.set(admin);
            when(activityService.approveActivity(10L))
                    .thenThrow(new RuntimeException("数据库连接超时"));

            assertThatThrownBy(() -> controller.approveActivity(10L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("数据库连接超时");
        }
    }

    // ========================================================================
    // createActivity — 创建活动
    // ========================================================================

    @Nested
    class CreateActivity {

        @Test
        void merchant_canCreate() {
            SecurityContext.set(merchant);
            CreateActivityRequest request = new CreateActivityRequest();
            when(activityService.createActivity(1L, request))
                    .thenReturn(mockActivityVO(100L, "draft"));

            Result<ActivityVO> result = controller.createActivity(request);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData().getActivityId()).isEqualTo(100L);
            assertThat(result.getData().getStatus()).isEqualTo("draft");
        }

        @Test
        void noAuth_throws() {
            assertThatThrownBy(() -> controller.createActivity(new CreateActivityRequest()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("未登录");
        }

        @Test
        void user_throws() {
            SecurityContext.set(user);
            assertThatThrownBy(() -> controller.createActivity(new CreateActivityRequest()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("仅商家可创建活动");
        }

        @Test
        void admin_throws() {
            SecurityContext.set(admin);
            assertThatThrownBy(() -> controller.createActivity(new CreateActivityRequest()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("仅商家可创建活动");
        }

        @Test
        void merchantId_fromSecurityContext() {
            SecurityContext.set(new CurrentUser(5L, "商家B", UserRole.merchant));
            CreateActivityRequest request = new CreateActivityRequest();
            when(activityService.createActivity(5L, request))
                    .thenReturn(mockActivityVO(200L, "draft"));

            controller.createActivity(request);

            verify(activityService).createActivity(5L, request);
        }

        @Test
        void businessException_propagates() {
            SecurityContext.set(merchant);
            CreateActivityRequest request = new CreateActivityRequest();
            when(activityService.createActivity(1L, request))
                    .thenThrow(new BusinessException("活动名不能为空"));

            assertThatThrownBy(() -> controller.createActivity(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("活动名不能为空");
        }

        @Test
        void runtimeException_propagates() {
            SecurityContext.set(merchant);
            CreateActivityRequest request = new CreateActivityRequest();
            when(activityService.createActivity(1L, request))
                    .thenThrow(new RuntimeException("DB error"));

            assertThatThrownBy(() -> controller.createActivity(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB error");
        }
    }

    // ========================================================================
    // listActivities — 活动列表（角色路由）
    // ========================================================================

    @Nested
    class ListActivities {

        @Test
        void merchant_listsOwn() {
            SecurityContext.set(merchant);
            PageVO<ActivityVO> pageVO = new PageVO<>(List.of(), 0, 1, 10);
            when(activityService.listMerchantActivities(1L, 1, 10)).thenReturn(pageVO);

            Result<PageVO<ActivityVO>> result = controller.listActivities(1, 10);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isSameAs(pageVO);
            verify(activityService).listMerchantActivities(1L, 1, 10);
        }

        @Test
        void admin_listsAll() {
            SecurityContext.set(admin);
            PageVO<ActivityVO> pageVO = new PageVO<>(List.of(), 0, 1, 10);
            when(activityService.listAllActivities(1, 10)).thenReturn(pageVO);

            controller.listActivities(1, 10);

            verify(activityService).listAllActivities(1, 10);
        }

        @Test
        void user_listsActive() {
            SecurityContext.set(user);
            PageVO<ActivityVO> pageVO = new PageVO<>(List.of(), 0, 1, 10);
            when(activityService.listActiveActivities(1, 10)).thenReturn(pageVO);

            controller.listActivities(1, 10);

            verify(activityService).listActiveActivities(1, 10);
        }

        @Test
        void noAuth_throws() {
            assertThatThrownBy(() -> controller.listActivities(1, 10))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("未登录");
        }

        @Test
        void emptyResult_returnsEmptyList() {
            SecurityContext.set(merchant);
            PageVO<ActivityVO> empty = new PageVO<>(List.of(), 0L, 1, 10);
            when(activityService.listMerchantActivities(1L, 1, 10)).thenReturn(empty);

            Result<PageVO<ActivityVO>> result = controller.listActivities(1, 10);

            assertThat(result.getData().getRecords()).isEmpty();
            assertThat(result.getData().getTotal()).isZero();
        }

        @Test
        void businessException_propagates() {
            SecurityContext.set(merchant);
            when(activityService.listMerchantActivities(1L, 1, 10))
                    .thenThrow(new BusinessException("查询失败"));

            assertThatThrownBy(() -> controller.listActivities(1, 10))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("查询失败");
        }
    }

    // ========================================================================
    // getActivityDetail — 活动详情
    // ========================================================================

    @Nested
    class GetActivityDetail {

        @Test
        void authenticated_canView() {
            SecurityContext.set(user);
            when(activityService.getActivityDetail(10L)).thenReturn(mockActivityVO(10L, "running"));

            Result<ActivityVO> result = controller.getActivityDetail(10L);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData().getActivityId()).isEqualTo(10L);
        }

        @Test
        void noAuth_throws() {
            assertThatThrownBy(() -> controller.getActivityDetail(10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("未登录");
        }

        @Test
        void activityId_passedToService() {
            SecurityContext.set(user);
            when(activityService.getActivityDetail(99L)).thenReturn(mockActivityVO(99L, "draft"));

            controller.getActivityDetail(99L);

            verify(activityService).getActivityDetail(99L);
        }

        @Test
        void notFound_propagates() {
            SecurityContext.set(user);
            when(activityService.getActivityDetail(999L))
                    .thenThrow(new BusinessException("活动不存在"));

            assertThatThrownBy(() -> controller.getActivityDetail(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("活动不存在");
        }

        @Test
        void runtimeException_propagates() {
            SecurityContext.set(user);
            when(activityService.getActivityDetail(10L))
                    .thenThrow(new RuntimeException("DB error"));

            assertThatThrownBy(() -> controller.getActivityDetail(10L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB error");
        }
    }
}
