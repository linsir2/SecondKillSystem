package com.seckill.module.activity.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.module.activity.mapper.ActivityMapper;
import com.seckill.module.activity.mapper.SeckillGoodsMapper;
import com.seckill.module.activity.model.dto.CreateActivityRequest;
import com.seckill.module.activity.model.dto.CreateSeckillGoodsItem;
import com.seckill.module.activity.model.entity.Activity;
import com.seckill.module.activity.model.entity.SeckillGoods;
import com.seckill.module.activity.model.enums.ActivityStatus;
import com.seckill.module.activity.model.vo.ActivityVO;
import com.seckill.module.goods.model.dto.GoodsInfo;
import com.seckill.module.goods.service.GoodsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 活动上下文 — 创建草稿。
 *
 * <p>样式: 纯 unit test + Mockito，无 Spring 上下文。
 *
 * <p><b>createActivity</b>：只做本地校验，不调远程。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityService")
class ActivityServiceTest {

    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private SeckillGoodsMapper seckillGoodsMapper;
    @Mock
    private GoodsService goodsService;

    @InjectMocks
    private ActivityService activityService;

    private final Long merchantId = 1L;

    // ========================================================================
    // 1. 创建活动 — 快乐路径
    // ========================================================================

    @Nested
    @DisplayName("创建活动")
    class CreateActivity {

        @Nested
        @DisplayName("快乐路径")
        class HappyPath {

            @Test
            @DisplayName("单商品 description=null → activity+seckillGoods 入库，VO 返回")
            void singleGoods() {
                var item = new CreateSeckillGoodsItem(100L, new BigDecimal("9.90"), 50, 1);
                var request = new CreateActivityRequest(
                        "国庆秒杀",
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(2),
                        null,
                        List.of(item));

                ActivityVO vo = activityService.createActivity(merchantId, request);

                // — VO（创建时不调 goodsService，goodsName 为 null）—
                assertThat(vo.getActivityName()).isEqualTo("国庆秒杀");
                assertThat(vo.getMerchantId()).isEqualTo(merchantId);
                assertThat(vo.getStatus()).isEqualTo("draft");
                assertThat(vo.getDescription()).isNull();
                assertThat(vo.getSeckillGoodsList()).hasSize(1);
                assertThat(vo.getSeckillGoodsList().get(0).getGoodsName()).isNull();
                assertThat(vo.getSeckillGoodsList().get(0).getSeckillPrice()).isEqualByComparingTo("9.90");
                assertThat(vo.getSeckillGoodsList().get(0).getStock()).isEqualTo(50);
                assertThat(vo.getSeckillGoodsList().get(0).getLimitNum()).isEqualTo(1);

                // — activity 入库 —
                var activityArg = ArgumentCaptor.forClass(Activity.class);
                verify(activityMapper).insert(activityArg.capture());
                assertThat(activityArg.getValue().getActivityName()).isEqualTo("国庆秒杀");
                assertThat(activityArg.getValue().getStatus()).isEqualTo(ActivityStatus.draft);

                // — seckillGoods 入库 —
                var goodsArg = ArgumentCaptor.forClass(SeckillGoods.class);
                verify(seckillGoodsMapper).insert(goodsArg.capture());
                assertThat(goodsArg.getValue().getGoodsId()).isEqualTo(100L);
                assertThat(goodsArg.getValue().getSeckillPrice()).isEqualByComparingTo("9.90");
                assertThat(goodsArg.getValue().getStock()).isEqualTo(50);
                assertThat(goodsArg.getValue().getLimitNum()).isEqualTo(1);
            }

            @Test
            @DisplayName("多商品 description 有值 → 全部入库，VO 带回基本信息")
            void multipleGoods() {
                var item1 = new CreateSeckillGoodsItem(100L, new BigDecimal("9.90"), 50, 1);
                var item2 = new CreateSeckillGoodsItem(101L, new BigDecimal("19.90"), 30, 2);
                var request = new CreateActivityRequest(
                        "双商品秒杀",
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(2),
                        "测试描述",
                        List.of(item1, item2));

                ActivityVO vo = activityService.createActivity(merchantId, request);

                assertThat(vo.getActivityName()).isEqualTo("双商品秒杀");
                assertThat(vo.getDescription()).isEqualTo("测试描述");
                assertThat(vo.getSeckillGoodsList()).hasSize(2);
                assertThat(vo.getSeckillGoodsList().get(0).getGoodsName()).isNull();
                assertThat(vo.getSeckillGoodsList().get(1).getGoodsName()).isNull();
                verify(activityMapper, times(1)).insert(any(Activity.class));
                verify(seckillGoodsMapper, times(2)).insert(any(SeckillGoods.class));
            }

            @Test
            @DisplayName("description 为空字符串 → 可选字段不应拒绝")
            void emptyDescription() {
                var item = new CreateSeckillGoodsItem(100L, new BigDecimal("9.90"), 50, 1);
                var request = new CreateActivityRequest(
                        "活动", LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
                        "", List.of(item));

                ActivityVO vo = activityService.createActivity(merchantId, request);

                assertThat(vo.getDescription()).isEqualTo("");
                verify(activityMapper).insert(any(Activity.class));
            }
        }

        // ================================================================
        // 1b. 时间窗口校验
        // ================================================================

        @Nested
        @DisplayName("时间校验")
        class TimeValidation {

            @Test
            @DisplayName("end_time = start_time → IllegalArgumentException（零长度活动）")
            void endTimeEqualsStartTime() {
                var start = LocalDateTime.now().plusDays(1);
                var request = new CreateActivityRequest(
                        "测试", start, start, null, List.of(validItem()));

                assertThatThrownBy(() -> activityService.createActivity(merchantId, request))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("结束时间");
            }
        }

        // ================================================================
        // 1c. 商品列表校验
        // ================================================================

        @Nested
        @DisplayName("商品列表校验")
        class GoodsListValidation {

            @Test
            @DisplayName("seckillGoodsList 为空 → IllegalArgumentException")
            void emptyList() {
                var request = new CreateActivityRequest(
                        "测试", LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
                        null, Collections.emptyList());

                assertThatThrownBy(() -> activityService.createActivity(merchantId, request))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("至少");
            }

            @Test
            @DisplayName("seckillGoodsList 为 null → IllegalArgumentException")
            void nullList() {
                var request = new CreateActivityRequest(
                        "测试", LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
                        null, null);

                assertThatThrownBy(() -> activityService.createActivity(merchantId, request))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("至少");
            }

            @Test
            @DisplayName("重复 goods_id → BusinessException，不入库")
            void duplicateGoodsId() {
                var item1 = new CreateSeckillGoodsItem(100L, new BigDecimal("9.90"), 30, 1);
                var item2 = new CreateSeckillGoodsItem(100L, new BigDecimal("8.00"), 20, 2);
                var request = new CreateActivityRequest(
                        "测试", LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
                        null, List.of(item1, item2));

                assertThatThrownBy(() -> activityService.createActivity(merchantId, request))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("重复");

                verify(activityMapper, never()).insert(any(Activity.class));
                verify(seckillGoodsMapper, never()).insert(any(SeckillGoods.class));
            }
        }

        // ================================================================
        // 1d. 库存/价格/限购边界
        // ================================================================

        @Nested
        @DisplayName("库存/价格/限购校验")
        class StockPriceLimit {

            @Test
            @DisplayName("seckillPrice = 0 → IllegalArgumentException")
            void zeroPrice() {
                var item = new CreateSeckillGoodsItem(100L, BigDecimal.ZERO, 50, 1);
                var request = requestWithItem(item);

                assertThatThrownBy(() -> activityService.createActivity(merchantId, request))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("秒杀价");
            }

            @Test
            @DisplayName("seckillPrice 负数 → IllegalArgumentException")
            void negativePrice() {
                var item = new CreateSeckillGoodsItem(100L, new BigDecimal("-1.00"), 50, 1);
                var request = requestWithItem(item);

                assertThatThrownBy(() -> activityService.createActivity(merchantId, request))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("秒杀价");
            }

            @Test
            @DisplayName("stock = 0 → IllegalArgumentException")
            void zeroStock() {
                var item = new CreateSeckillGoodsItem(100L, new BigDecimal("9.90"), 0, 1);
                var request = requestWithItem(item);

                assertThatThrownBy(() -> activityService.createActivity(merchantId, request))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("库存");
            }

            @Test
            @DisplayName("stock 负数 → IllegalArgumentException")
            void negativeStock() {
                var item = new CreateSeckillGoodsItem(100L, new BigDecimal("9.90"), -5, 1);
                var request = requestWithItem(item);

                assertThatThrownBy(() -> activityService.createActivity(merchantId, request))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("库存");
            }

            @Test
            @DisplayName("limitNum = 0 → IllegalArgumentException")
            void zeroLimitNum() {
                var item = new CreateSeckillGoodsItem(100L, new BigDecimal("9.90"), 50, 0);
                var request = requestWithItem(item);

                assertThatThrownBy(() -> activityService.createActivity(merchantId, request))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("限购");
            }

            @Test
            @DisplayName("limitNum 负数 → IllegalArgumentException")
            void negativeLimitNum() {
                var item = new CreateSeckillGoodsItem(100L, new BigDecimal("9.90"), 50, -1);
                var request = requestWithItem(item);

                assertThatThrownBy(() -> activityService.createActivity(merchantId, request))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("限购");
            }
        }

        // ================================================================
        // 1e. 字段格式
        // ================================================================

        @Nested
        @DisplayName("字段格式校验")
        class FieldFormat {

            @Test
            @DisplayName("activity_name 全空白 → IllegalArgumentException")
            void blankName() {
                var request = new CreateActivityRequest(
                        "   ", LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
                        null, List.of(validItem()));

                assertThatThrownBy(() -> activityService.createActivity(merchantId, request))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("活动名");
            }

            @Test
            @DisplayName("activity_name 超过 255 字符 → IllegalArgumentException")
            void nameTooLong() {
                var request = new CreateActivityRequest(
                        "a".repeat(256), LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
                        null, List.of(validItem()));

                assertThatThrownBy(() -> activityService.createActivity(merchantId, request))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("活动名");
            }
        }
    }

    // ========================================================================
    // 2. 提交审核
    // ========================================================================

    @Nested
    @DisplayName("提交审核")
    class SubmitForReview {

        private final Long activityId = 10L;

        private Activity draftActivity() {
            Activity a = new Activity();
            a.setActivityId(activityId);
            a.setMerchantId(merchantId);
            a.setActivityName("国庆秒杀");
            a.setStatus(ActivityStatus.draft);
            a.setStartTime(LocalDateTime.now().plusDays(1));
            a.setEndTime(LocalDateTime.now().plusDays(2));
            a.setCreatedAt(LocalDateTime.now());
            return a;
        }

        @Nested
        @DisplayName("快乐路径")
        class HappyPath {

            @Test
            @DisplayName("单商品 → status=pending, goodsName 已填充")
            void singleGoods() {
                Activity activity = draftActivity();
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                SeckillGoods sg = seckillGoods(100L, 200L, new BigDecimal("9.90"), 50, 1);
                sg.setActivityId(activityId);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg));

                GoodsInfo goodsInfo = new GoodsInfo(200L, "测试商品", new BigDecimal("99.00"), 100);
                when(goodsService.getGoodsInfoList(List.of(200L), merchantId)).thenReturn(List.of(goodsInfo));

                when(activityMapper.update(any(Activity.class), any())).thenReturn(1);

                ActivityVO vo = activityService.submitForReview(merchantId, activityId);

                assertThat(vo.getStatus()).isEqualTo("pending");
                assertThat(vo.getSeckillGoodsList()).hasSize(1);
                assertThat(vo.getSeckillGoodsList().get(0).getGoodsName()).isEqualTo("测试商品");
                assertThat(vo.getActivityName()).isEqualTo("国庆秒杀");
            }

            @Test
            @DisplayName("多商品 → 所有 goodsName 均填充")
            void multipleGoods() {
                Activity activity = draftActivity();
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                SeckillGoods sg1 = seckillGoods(100L, 200L, new BigDecimal("9.90"), 50, 1);
                SeckillGoods sg2 = seckillGoods(101L, 201L, new BigDecimal("19.90"), 30, 2);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg1, sg2));

                GoodsInfo info1 = new GoodsInfo(200L, "商品A", new BigDecimal("99.00"), 100);
                GoodsInfo info2 = new GoodsInfo(201L, "商品B", new BigDecimal("199.00"), 50);
                when(goodsService.getGoodsInfoList(List.of(200L, 201L), merchantId))
                        .thenReturn(List.of(info1, info2));

                when(activityMapper.update(any(Activity.class), any())).thenReturn(1);

                ActivityVO vo = activityService.submitForReview(merchantId, activityId);

                assertThat(vo.getStatus()).isEqualTo("pending");
                assertThat(vo.getSeckillGoodsList()).hasSize(2);
                assertThat(vo.getSeckillGoodsList().get(0).getGoodsName()).isEqualTo("商品A");
                assertThat(vo.getSeckillGoodsList().get(1).getGoodsName()).isEqualTo("商品B");
            }
        }

        @Nested
        @DisplayName("前置条件校验")
        class Preconditions {

            @Test
            @DisplayName("活动不存在 → BusinessException")
            void activityNotFound() {
                when(activityMapper.selectById(activityId)).thenReturn(null);

                assertThatThrownBy(() -> activityService.submitForReview(merchantId, activityId))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("不存在");
            }

            @Test
            @DisplayName("非本人活动 → BusinessException")
            void notOwner() {
                Activity activity = draftActivity();
                activity.setMerchantId(999L);
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                assertThatThrownBy(() -> activityService.submitForReview(merchantId, activityId))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("无权");
            }

            @ParameterizedTest
            @EnumSource(value = ActivityStatus.class, names = {"pending", "preheating", "running", "ended"})
            @DisplayName("状态不是 draft → BusinessException")
            void notDraft(ActivityStatus status) {
                Activity activity = draftActivity();
                activity.setStatus(status);
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                assertThatThrownBy(() -> activityService.submitForReview(merchantId, activityId))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("当前状态");
            }

            @Test
            @DisplayName("startTime 已过期 → IllegalArgumentException")
            void startTimeExpired() {
                Activity activity = draftActivity();
                activity.setStartTime(LocalDateTime.now().minusDays(1));
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                assertThatThrownBy(() -> activityService.submitForReview(merchantId, activityId))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("过期");
            }
        }

        @Nested
        @DisplayName("商品校验")
        class GoodsValidation {

            @Test
            @DisplayName("seckillGoods 列表为空 → BusinessException + errors")
            void emptySeckillGoods() {
                Activity activity = draftActivity();
                when(activityMapper.selectById(activityId)).thenReturn(activity);
                when(seckillGoodsMapper.selectList(any())).thenReturn(Collections.emptyList());

                BusinessException thrown = catchThrowableOfType(
                        () -> activityService.submitForReview(merchantId, activityId),
                        BusinessException.class);
                assertThat(thrown.getErrors()).isNotEmpty();
                assertThat(thrown.getErrors().get(0)).contains("商品");
            }

            @Test
            @DisplayName("部分商品不存在 → errors 含具体 ID")
            void goodsNotFound() {
                Activity activity = draftActivity();
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                SeckillGoods sg1 = seckillGoods(100L, 200L, new BigDecimal("9.90"), 50, 1);
                SeckillGoods sg2 = seckillGoods(101L, 300L, new BigDecimal("19.90"), 30, 2);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg1, sg2));

                GoodsInfo info1 = new GoodsInfo(200L, "商品A", new BigDecimal("99.00"), 100);
                when(goodsService.getGoodsInfoList(List.of(200L, 300L), merchantId))
                        .thenReturn(Arrays.asList(info1, null));

                BusinessException thrown = catchThrowableOfType(
                        () -> activityService.submitForReview(merchantId, activityId),
                        BusinessException.class);
                assertThat(thrown.getErrors()).hasSize(1);
                assertThat(thrown.getErrors().get(0)).contains("300");
            }

            @Test
            @DisplayName("秒杀库存超过日常库存 → errors 含具体说明")
            void stockExceeds() {
                Activity activity = draftActivity();
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                SeckillGoods sg = seckillGoods(100L, 200L, new BigDecimal("9.90"), 999, 1);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg));

                GoodsInfo info = new GoodsInfo(200L, "限量商品", new BigDecimal("99.00"), 100);
                when(goodsService.getGoodsInfoList(List.of(200L), merchantId))
                        .thenReturn(List.of(info));

                BusinessException thrown = catchThrowableOfType(
                        () -> activityService.submitForReview(merchantId, activityId),
                        BusinessException.class);
                assertThat(thrown.getErrors()).hasSize(1);
                assertThat(thrown.getErrors().get(0))
                        .contains("限量商品").contains("999").contains("100");
            }

            @Test
            @DisplayName("混合错误（不存在 + 超限）→ errors 收集全部")
            void mixedErrors() {
                Activity activity = draftActivity();
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                SeckillGoods sg1 = seckillGoods(100L, 200L, new BigDecimal("9.90"), 999, 1);
                SeckillGoods sg2 = seckillGoods(101L, 300L, new BigDecimal("19.90"), 30, 2);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg1, sg2));

                GoodsInfo info1 = new GoodsInfo(200L, "限量商品", new BigDecimal("99.00"), 100);
                when(goodsService.getGoodsInfoList(List.of(200L, 300L), merchantId))
                        .thenReturn(Arrays.asList(info1, null));

                BusinessException thrown = catchThrowableOfType(
                        () -> activityService.submitForReview(merchantId, activityId),
                        BusinessException.class);
                assertThat(thrown.getErrors()).hasSize(2);
            }
        }

        @Nested
        @DisplayName("乐观锁冲突")
        class OptimisticLock {

            @Test
            @DisplayName("affectedRows=0 → BusinessException")
            void lockConflict() {
                Activity activity = draftActivity();
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                SeckillGoods sg = seckillGoods(100L, 200L, new BigDecimal("9.90"), 50, 1);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg));

                GoodsInfo goodsInfo = new GoodsInfo(200L, "测试商品", new BigDecimal("99.00"), 100);
                when(goodsService.getGoodsInfoList(List.of(200L), merchantId)).thenReturn(List.of(goodsInfo));

                when(activityMapper.update(any(Activity.class), any())).thenReturn(0);

                assertThatThrownBy(() -> activityService.submitForReview(merchantId, activityId))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("提交失败");
            }
        }
    }

    // ========================================================================
    // 3. 审核通过
    // ========================================================================

    @Nested
    @DisplayName("审核通过")
    class ApproveActivity {

        private final Long activityId = 20L;

        private Activity pendingActivity() {
            Activity a = new Activity();
            a.setActivityId(activityId);
            a.setMerchantId(merchantId);
            a.setActivityName("国庆秒杀");
            a.setStatus(ActivityStatus.pending);
            a.setStartTime(LocalDateTime.now().plusDays(1));
            a.setEndTime(LocalDateTime.now().plusDays(2));
            a.setCreatedAt(LocalDateTime.now());
            return a;
        }

        @Nested
        @DisplayName("快乐路径")
        class HappyPath {

            @Test
            @DisplayName("单商品 → status=preheating, goodsName 已填充, deductStock 被调用")
            void singleGoods() {
                Activity activity = pendingActivity();
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                SeckillGoods sg = seckillGoods(100L, 200L, new BigDecimal("9.90"), 50, 1);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg));

                GoodsInfo info = new GoodsInfo(200L, "测试商品", new BigDecimal("99.00"), 100);
                when(goodsService.getGoodsInfoList(List.of(200L), merchantId)).thenReturn(List.of(info));

                when(activityMapper.update(any(Activity.class), any())).thenReturn(1);

                ActivityVO vo = activityService.approveActivity(activityId);

                assertThat(vo.getStatus()).isEqualTo("preheating");
                assertThat(vo.getSeckillGoodsList()).hasSize(1);
                assertThat(vo.getSeckillGoodsList().get(0).getGoodsName()).isEqualTo("测试商品");
                assertThat(vo.getActivityName()).isEqualTo("国庆秒杀");

                verify(goodsService).deductStock(200L, merchantId, 50);
            }

            @Test
            @DisplayName("多商品 → 所有 goodsName 均填充，所有商品 deductStock")
            void multipleGoods() {
                Activity activity = pendingActivity();
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                SeckillGoods sg1 = seckillGoods(100L, 200L, new BigDecimal("9.90"), 50, 1);
                SeckillGoods sg2 = seckillGoods(101L, 201L, new BigDecimal("19.90"), 30, 2);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg1, sg2));

                GoodsInfo info1 = new GoodsInfo(200L, "商品A", new BigDecimal("99.00"), 100);
                GoodsInfo info2 = new GoodsInfo(201L, "商品B", new BigDecimal("199.00"), 50);
                when(goodsService.getGoodsInfoList(List.of(200L, 201L), merchantId))
                        .thenReturn(List.of(info1, info2));

                when(activityMapper.update(any(Activity.class), any())).thenReturn(1);

                ActivityVO vo = activityService.approveActivity(activityId);

                assertThat(vo.getStatus()).isEqualTo("preheating");
                assertThat(vo.getSeckillGoodsList()).hasSize(2);
                assertThat(vo.getSeckillGoodsList().get(0).getGoodsName()).isEqualTo("商品A");
                assertThat(vo.getSeckillGoodsList().get(1).getGoodsName()).isEqualTo("商品B");

                verify(goodsService).deductStock(200L, merchantId, 50);
                verify(goodsService).deductStock(201L, merchantId, 30);
            }
        }

        @Nested
        @DisplayName("前置条件校验")
        class Preconditions {

            @Test
            @DisplayName("活动不存在 → BusinessException")
            void activityNotFound() {
                when(activityMapper.selectById(activityId)).thenReturn(null);

                assertThatThrownBy(() -> activityService.approveActivity(activityId))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("不存在");
            }

            @ParameterizedTest
            @EnumSource(value = ActivityStatus.class, names = {"draft", "preheating", "running", "ended"})
            @DisplayName("状态不是 pending → BusinessException")
            void notPending(ActivityStatus status) {
                Activity activity = pendingActivity();
                activity.setStatus(status);
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                assertThatThrownBy(() -> activityService.approveActivity(activityId))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("不可审核");
            }

            @Test
            @DisplayName("startTime 已过期 → IllegalArgumentException")
            void startTimeExpired() {
                Activity activity = pendingActivity();
                activity.setStartTime(LocalDateTime.now().minusDays(1));
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                assertThatThrownBy(() -> activityService.approveActivity(activityId))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("过期");
            }
        }

        @Nested
        @DisplayName("商品校验")
        class GoodsValidation {

            @Test
            @DisplayName("部分商品不存在 → errors 含具体 ID")
            void goodsNotFound() {
                Activity activity = pendingActivity();
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                SeckillGoods sg = seckillGoods(100L, 200L, new BigDecimal("9.90"), 50, 1);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg));

                when(goodsService.getGoodsInfoList(List.of(200L), merchantId))
                        .thenReturn(Arrays.asList((GoodsInfo) null));

                BusinessException thrown = catchThrowableOfType(
                        () -> activityService.approveActivity(activityId),
                        BusinessException.class);
                assertThat(thrown.getErrors()).hasSize(1);
                assertThat(thrown.getErrors().get(0)).contains("200");
                verify(goodsService, never()).deductStock(anyLong(), anyLong(), anyInt());
            }

            @Test
            @DisplayName("库存不足 → errors 含具体说明")
            void stockInsufficient() {
                Activity activity = pendingActivity();
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                SeckillGoods sg = seckillGoods(100L, 200L, new BigDecimal("9.90"), 50, 1);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg));

                GoodsInfo info = new GoodsInfo(200L, "限量商品", new BigDecimal("99.00"), 30);
                when(goodsService.getGoodsInfoList(List.of(200L), merchantId))
                        .thenReturn(List.of(info));

                BusinessException thrown = catchThrowableOfType(
                        () -> activityService.approveActivity(activityId),
                        BusinessException.class);
                assertThat(thrown.getErrors()).hasSize(1);
                assertThat(thrown.getErrors().get(0))
                        .contains("限量商品").contains("50");
                verify(goodsService, never()).deductStock(anyLong(), anyLong(), anyInt());
            }

            @Test
            @DisplayName("混合错误（不存在 + 超限）→ errors 收集全部")
            void mixedErrors() {
                Activity activity = pendingActivity();
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                SeckillGoods sg1 = seckillGoods(100L, 200L, new BigDecimal("9.90"), 999, 1);
                SeckillGoods sg2 = seckillGoods(101L, 300L, new BigDecimal("19.90"), 30, 2);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg1, sg2));

                GoodsInfo info1 = new GoodsInfo(200L, "限量商品", new BigDecimal("99.00"), 100);
                when(goodsService.getGoodsInfoList(List.of(200L, 300L), merchantId))
                        .thenReturn(Arrays.asList(info1, null));

                BusinessException thrown = catchThrowableOfType(
                        () -> activityService.approveActivity(activityId),
                        BusinessException.class);
                assertThat(thrown.getErrors()).hasSize(2);
                verify(goodsService, never()).deductStock(anyLong(), anyLong(), anyInt());
            }
        }

        @Nested
        @DisplayName("乐观锁冲突")
        class OptimisticLock {

            @Test
            @DisplayName("affectedRows=0 → BusinessException，deductStock 已调用（事务会回滚）")
            void lockConflict() {
                Activity activity = pendingActivity();
                when(activityMapper.selectById(activityId)).thenReturn(activity);

                SeckillGoods sg = seckillGoods(100L, 200L, new BigDecimal("9.90"), 50, 1);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg));

                GoodsInfo info = new GoodsInfo(200L, "测试商品", new BigDecimal("99.00"), 100);
                when(goodsService.getGoodsInfoList(List.of(200L), merchantId))
                        .thenReturn(List.of(info));

                when(activityMapper.update(any(Activity.class), any())).thenReturn(0);

                assertThatThrownBy(() -> activityService.approveActivity(activityId))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("审核失败");

                verify(goodsService).deductStock(200L, merchantId, 50);
            }
        }
    }

    // ========================================================================
    // Helpers — 外部类工具方法，所有内部类共享
    // ========================================================================

    private SeckillGoods seckillGoods(Long sgId, Long goodsId, BigDecimal price, Integer stock, Integer limit) {
        SeckillGoods sg = new SeckillGoods();
        sg.setSeckillGoodsId(sgId);
        sg.setGoodsId(goodsId);
        sg.setSeckillPrice(price);
        sg.setStock(stock);
        sg.setLimitNum(limit);
        return sg;
    }

    // ========================================================================
    // Helper
    // ========================================================================

    private static CreateSeckillGoodsItem validItem() {
        return new CreateSeckillGoodsItem(100L, new BigDecimal("9.90"), 50, 1);
    }

    private static CreateActivityRequest requestWithItem(CreateSeckillGoodsItem item) {
        return new CreateActivityRequest(
                "测试", LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
                null, List.of(item));
    }
}
