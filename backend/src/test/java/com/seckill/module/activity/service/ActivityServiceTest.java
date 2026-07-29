package com.seckill.module.activity.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.PageVO;
import com.seckill.module.activity.mapper.ActivityMapper;
import com.seckill.module.activity.mapper.SeckillGoodsMapper;
import com.seckill.module.activity.model.dto.ActivityApprovedEvent;
import com.seckill.module.activity.model.dto.ActivitySubmittedForReviewEvent;
import com.seckill.module.activity.model.dto.CreateActivityRequest;
import com.seckill.module.activity.model.dto.CreateSeckillGoodsItem;
import com.seckill.module.activity.model.entity.Activity;
import com.seckill.module.activity.model.entity.SeckillGoods;
import com.seckill.module.activity.model.enums.ActivityStatus;
import com.seckill.module.activity.model.vo.ActivityVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seckill.module.gateway.service.BlacklistLoader;
import com.seckill.module.goods.model.dto.GoodsInfo;
import com.seckill.module.goods.service.GoodsService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
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
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;
    @Mock
    private BlacklistLoader blacklistLoader;

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

                verify(eventPublisher).publishEvent(any(ActivitySubmittedForReviewEvent.class));
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

                verify(eventPublisher).publishEvent(any(ActivitySubmittedForReviewEvent.class));
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
                // 实体方法在 goods 校验之后执行，需要 mock 让商品校验通过
                SeckillGoods sg = new SeckillGoods();
                sg.setSeckillGoodsId(100L);
                sg.setGoodsId(200L);
                sg.setSeckillPrice(new BigDecimal("9.90"));
                sg.setStock(50);
                sg.setLimitNum(1);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg));
                when(goodsService.getGoodsInfoList(anyList(), anyLong())).thenReturn(
                        List.of(new GoodsInfo(200L, "测试商品", new BigDecimal("99.00"), 100)));

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
                verify(eventPublisher).publishEvent(any(ActivityApprovedEvent.class));
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
                verify(eventPublisher).publishEvent(any(ActivityApprovedEvent.class));
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
                // 实体方法在 goods 校验 + 扣库存之后执行，需要 mock 让前置步骤通过
                SeckillGoods sg = new SeckillGoods();
                sg.setSeckillGoodsId(100L);
                sg.setGoodsId(200L);
                sg.setSeckillPrice(new BigDecimal("9.90"));
                sg.setStock(50);
                sg.setLimitNum(1);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg));
                when(goodsService.getGoodsInfoList(anyList(), anyLong())).thenReturn(
                        List.of(new GoodsInfo(200L, "测试商品", new BigDecimal("99.00"), 100)));

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
    // preheatActivity
    // ========================================================================

    @Nested
    @DisplayName("preheatActivity")
    class PreheatActivity {

        private final Long activityId = 10L;

        @Test
        @DisplayName("快乐路径：SET stock/limit + 调 BlacklistLoader 刷新黑名单")
        void happyPath() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);

            SeckillGoods sg = new SeckillGoods();
            sg.setSeckillGoodsId(100L);
            sg.setStock(50);
            sg.setLimitNum(1);
            when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg));

            activityService.preheatActivity(activityId);

            verify(valueOps).set("seckill:stock:" + activityId + ":100", "50");
            verify(valueOps).set("seckill:limit:" + activityId + ":100", "1");
            verify(blacklistLoader).load();
        }

        @Test
        @DisplayName("无秒杀商品 → 不写 stock key，只调 BlacklistLoader")
        void emptyGoods() {
            when(seckillGoodsMapper.selectList(any())).thenReturn(List.of());

            activityService.preheatActivity(activityId);

            verify(valueOps, never()).set(anyString(), anyString());
            verify(blacklistLoader).load();
        }
    }

    // ========================================================================
    // startActivity
    // ========================================================================

    @Nested
    @DisplayName("startActivity")
    class StartActivity {

        private final Long activityId = 10L;

        private Activity preheatingActivity() {
            Activity a = new Activity();
            a.setActivityId(activityId);
            a.setStatus(ActivityStatus.preheating);
            a.setStartTime(LocalDateTime.now().plusMinutes(5));
            return a;
        }

        @Test
        @DisplayName("快乐路径：preheating → running")
        void happyPath() {
            Activity activity = preheatingActivity();
            when(activityMapper.selectById(activityId)).thenReturn(activity);
            when(activityMapper.update(any(Activity.class), any())).thenReturn(1);

            activityService.startActivity(activityId);

            assertThat(activity.getStatus()).isEqualTo(ActivityStatus.running);
            verify(activityMapper).update(eq(activity), any());
        }

        @Test
        @DisplayName("已是 running → 幂等跳过")
        void alreadyRunning() {
            Activity activity = preheatingActivity();
            activity.setStatus(ActivityStatus.running);
            when(activityMapper.selectById(activityId)).thenReturn(activity);

            activityService.startActivity(activityId);

            verify(activityMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("活动不存在 → 幂等跳过")
        void activityNotFound() {
            when(activityMapper.selectById(activityId)).thenReturn(null);

            activityService.startActivity(activityId);

            verify(activityMapper, never()).update(any(), any());
        }
    }

    // ========================================================================
    // endActivity
    // ========================================================================

    @Nested
    @DisplayName("endActivity")
    class EndActivity {

        private final Long activityId = 30L;

        private Activity runningActivity() {
            Activity a = new Activity();
            a.setActivityId(activityId);
            a.setStatus(ActivityStatus.running);
            return a;
        }

        @Test
        @DisplayName("A1 running → ended")
        void success() {
            Activity activity = runningActivity();
            when(activityMapper.selectById(activityId)).thenReturn(activity);
            when(activityMapper.update(any(Activity.class), any())).thenReturn(1);

            activityService.endActivity(activityId);

            assertThat(activity.getStatus()).isEqualTo(ActivityStatus.ended);
            verify(activityMapper).update(eq(activity), any());
        }

        @Test
        @DisplayName("A2 活动不存在 → 静默跳过")
        void notFound() {
            when(activityMapper.selectById(activityId)).thenReturn(null);

            activityService.endActivity(activityId);

            verify(activityMapper, never()).update(any(), any());
        }

        @ParameterizedTest
        @EnumSource(value = ActivityStatus.class, names = {"draft", "pending", "preheating", "ended"})
        @DisplayName("A3-A5 状态不是 running → 幂等跳过")
        void notRunningIdempotent(ActivityStatus status) {
            Activity activity = runningActivity();
            activity.setStatus(status);
            when(activityMapper.selectById(activityId)).thenReturn(activity);

            activityService.endActivity(activityId);

            verify(activityMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("A6 乐观锁冲突 → BusinessException")
        void lockConflict() {
            Activity activity = runningActivity();
            when(activityMapper.selectById(activityId)).thenReturn(activity);
            when(activityMapper.update(any(Activity.class), any())).thenReturn(0);

            assertThatThrownBy(() -> activityService.endActivity(activityId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("结束失败");
        }
    }

    // ========================================================================
    // restoreActivityStock
    // ========================================================================

    @Nested
    @DisplayName("restoreActivityStock")
    class RestoreActivityStock {

        private final Long activityId = 30L;
        private final Long sgId = 100L;
        private final Long goodsId = 200L;

        private Activity endedActivity() {
            Activity a = new Activity();
            a.setActivityId(activityId);
            a.setStatus(ActivityStatus.ended);
            return a;
        }

        private SeckillGoods newSg(Long sgId, Long goodsId) {
            SeckillGoods sg = new SeckillGoods();
            sg.setSeckillGoodsId(sgId);
            sg.setGoodsId(goodsId);
            return sg;
        }

        @Test
        @DisplayName("R1 1sg 剩余 stock=37 → restoreStock(37) + 4 key 清理")
        void singleSgPartialSold() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(activityMapper.selectById(activityId)).thenReturn(endedActivity());
            when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(newSg(sgId, goodsId)));
            when(valueOps.get("seckill:stock:30:100")).thenReturn("37");

            activityService.restoreActivityStock(activityId);

            verify(goodsService).restoreStock(goodsId, 37);
            verify(redisTemplate).delete("seckill:stock:30:100");
            verify(redisTemplate).delete("seckill:users:30:100");
            verify(redisTemplate).delete("seckill:limit:30:100");
            verify(redisTemplate).delete("seckill:pending:30");
        }

        @Test
        @DisplayName("R2 活动不存在 → 跳过")
        void activityNotFound() {
            when(activityMapper.selectById(activityId)).thenReturn(null);

            activityService.restoreActivityStock(activityId);

            verifyNoInteractions(seckillGoodsMapper, goodsService);
        }

        @Test
        @DisplayName("R3 状态不是 ended → 跳过")
        void notEnded() {
            Activity activity = endedActivity();
            activity.setStatus(ActivityStatus.running);
            when(activityMapper.selectById(activityId)).thenReturn(activity);

            activityService.restoreActivityStock(activityId);

            verify(seckillGoodsMapper, never()).selectList(any());
            verifyNoInteractions(goodsService);
        }

        @Test
        @DisplayName("R4 已回补（Redis stock key 不存在）→ 跳过此 sg")
        void alreadyRestored() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(activityMapper.selectById(activityId)).thenReturn(endedActivity());
            when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(newSg(sgId, goodsId)));
            when(valueOps.get("seckill:stock:30:100")).thenReturn(null);

            activityService.restoreActivityStock(activityId);

            verify(goodsService, never()).restoreStock(anyLong(), anyInt());
            verify(redisTemplate, never()).delete("seckill:stock:30:100");
            verify(redisTemplate, never()).delete("seckill:users:30:100");
            verify(redisTemplate, never()).delete("seckill:limit:30:100");
            verify(redisTemplate).delete("seckill:pending:30");
        }

        @Test
        @DisplayName("R5 3sg 混合（有库存 / 已回补 / 卖光）")
        void multipleSgsMixed() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(activityMapper.selectById(activityId)).thenReturn(endedActivity());
            when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(
                    newSg(100L, 200L), newSg(101L, 201L), newSg(102L, 202L)));

            when(valueOps.get("seckill:stock:30:100")).thenReturn("5");   // sg1: 5 remaining
            when(valueOps.get("seckill:stock:30:101")).thenReturn(null);  // sg2: already restored
            when(valueOps.get("seckill:stock:30:102")).thenReturn("0");   // sg3: sold out

            activityService.restoreActivityStock(activityId);

            // sg1 — restore + cleanup
            verify(goodsService).restoreStock(200L, 5);
            verify(redisTemplate).delete("seckill:stock:30:100");
            verify(redisTemplate).delete("seckill:users:30:100");
            verify(redisTemplate).delete("seckill:limit:30:100");
            // sg2 — skipped completely
            verify(redisTemplate, never()).delete("seckill:stock:30:101");
            // sg3 — stock=0, no restore, keys cleaned
            verify(goodsService, never()).restoreStock(eq(202L), anyInt());
            verify(redisTemplate).delete("seckill:stock:30:102");
            verify(redisTemplate).delete("seckill:users:30:102");
            verify(redisTemplate).delete("seckill:limit:30:102");
            // pending at end
            verify(redisTemplate).delete("seckill:pending:30");
        }

        @Test
        @DisplayName("R6 全卖光 stock=0 → 不回补，清理 key")
        void allSoldOut() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(activityMapper.selectById(activityId)).thenReturn(endedActivity());
            when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(newSg(sgId, goodsId)));
            when(valueOps.get("seckill:stock:30:100")).thenReturn("0");

            activityService.restoreActivityStock(activityId);

            verify(goodsService, never()).restoreStock(anyLong(), anyInt());
            verify(redisTemplate).delete("seckill:stock:30:100");
            verify(redisTemplate).delete("seckill:users:30:100");
            verify(redisTemplate).delete("seckill:limit:30:100");
            verify(redisTemplate).delete("seckill:pending:30");
        }

        @Test
        @DisplayName("R7 restoreStock 抛异常 → catch 跳过，不清理 key（留待重试）")
        void restoreStockThrows() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(activityMapper.selectById(activityId)).thenReturn(endedActivity());
            when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(newSg(sgId, goodsId)));
            when(valueOps.get("seckill:stock:30:100")).thenReturn("37");
            doThrow(new RuntimeException("DB error")).when(goodsService).restoreStock(goodsId, 37);

            activityService.restoreActivityStock(activityId);

            verify(goodsService).restoreStock(goodsId, 37);
            verify(redisTemplate, never()).delete("seckill:stock:30:100");
            verify(redisTemplate, never()).delete("seckill:users:30:100");
            verify(redisTemplate, never()).delete("seckill:limit:30:100");
            verify(redisTemplate).delete("seckill:pending:30");
        }

        @Test
        @DisplayName("R8 seckill_goods 为空 → 只清理 pending key")
        void emptySeckillGoods() {
            when(activityMapper.selectById(activityId)).thenReturn(endedActivity());
            when(seckillGoodsMapper.selectList(any())).thenReturn(List.of());

            activityService.restoreActivityStock(activityId);

            verifyNoInteractions(goodsService);
            verify(redisTemplate).delete("seckill:pending:30");
        }

        @Test
        @DisplayName("R9 Redis 值非法 → catch+log, 清理 key")
        void invalidRedisValue() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(activityMapper.selectById(activityId)).thenReturn(endedActivity());
            when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(newSg(sgId, goodsId)));
            when(valueOps.get("seckill:stock:30:100")).thenReturn("abc");

            activityService.restoreActivityStock(activityId);

            verify(goodsService, never()).restoreStock(anyLong(), anyInt());
            verify(redisTemplate).delete("seckill:stock:30:100");
            verify(redisTemplate).delete("seckill:users:30:100");
            verify(redisTemplate).delete("seckill:limit:30:100");
            verify(redisTemplate).delete("seckill:pending:30");
        }

        @Test
        @DisplayName("R10 所有 sg 的 stock key 都不存在 → 全部跳过")
        void allAlreadyRestored() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(activityMapper.selectById(activityId)).thenReturn(endedActivity());
            when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(
                    newSg(100L, 200L), newSg(101L, 201L)));

            when(valueOps.get("seckill:stock:30:100")).thenReturn(null);
            when(valueOps.get("seckill:stock:30:101")).thenReturn(null);

            activityService.restoreActivityStock(activityId);

            verifyNoInteractions(goodsService);
            verify(redisTemplate).delete("seckill:pending:30");
        }

        @Test
        @DisplayName("R11 Redis DEL 抛异常 → catch+log, 继续下一个 sg")
        void deleteThrows() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(activityMapper.selectById(activityId)).thenReturn(endedActivity());
            when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(
                    newSg(100L, 200L), newSg(101L, 201L)));

            when(valueOps.get("seckill:stock:30:100")).thenReturn("5");
            when(valueOps.get("seckill:stock:30:101")).thenReturn("3");

            doThrow(new RuntimeException("Redis timeout"))
                    .when(redisTemplate).delete("seckill:stock:30:100");

            activityService.restoreActivityStock(activityId);

            // sg1: restoreStock 调了，delete 抛异常 → 跳过清理
            verify(goodsService).restoreStock(200L, 5);
            // sg2: 正常
            verify(goodsService).restoreStock(201L, 3);
            // pending 始终清理
            verify(redisTemplate).delete("seckill:pending:30");
        }

        @Test
        @DisplayName("C1 两次顺序调用 → 第一次完整, 第二次跳过所有 sg")
        void twoSequentialCalls() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(activityMapper.selectById(activityId)).thenReturn(endedActivity());
            when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(newSg(sgId, goodsId)));

            // 第一次: stock 存在
            when(valueOps.get("seckill:stock:30:100")).thenReturn("13");

            activityService.restoreActivityStock(activityId);

            verify(goodsService).restoreStock(goodsId, 13);
            verify(redisTemplate).delete("seckill:stock:30:100");

            // 重置 mock 模拟第二次独立调用
            reset(goodsService, redisTemplate, valueOps);
            when(activityMapper.selectById(activityId)).thenReturn(endedActivity());
            when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(newSg(sgId, goodsId)));
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            // 第二次: stock key 已被清除 → null
            when(valueOps.get("seckill:stock:30:100")).thenReturn(null);

            activityService.restoreActivityStock(activityId);

            verify(goodsService, never()).restoreStock(anyLong(), anyInt());
            verify(redisTemplate).delete("seckill:pending:30");
        }
    }

    // ========================================================================
    // 4. 活动查询
    // ========================================================================

    @Nested
    @DisplayName("活动查询")
    class ActivityQuery {

        private final Long qMerchantId = 1L;
        private final Long qActivityId = 10L;

        private Activity qActivity(Long id, Long mId, ActivityStatus status) {
            Activity a = new Activity();
            a.setActivityId(id);
            a.setMerchantId(mId);
            a.setActivityName("测试活动");
            a.setStatus(status);
            a.setStartTime(LocalDateTime.now().plusDays(1));
            a.setEndTime(LocalDateTime.now().plusDays(2));
            a.setCreatedAt(LocalDateTime.now());
            return a;
        }

        // ================================================================
        // getActivityDetail
        // ================================================================

        @Nested
        @DisplayName("getActivityDetail")
        class GetActivityDetail {

            @Test
            @DisplayName("D1 正常 → 含 seckillGoodsList")
            void happyPath() {
                Activity activity = qActivity(qActivityId, qMerchantId, ActivityStatus.running);
                when(activityMapper.selectById(qActivityId)).thenReturn(activity);

                SeckillGoods sg = seckillGoods(100L, 200L, new BigDecimal("9.90"), 50, 1);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg));

                ActivityVO vo = activityService.getActivityDetail(qActivityId);

                assertThat(vo.getActivityId()).isEqualTo(qActivityId);
                assertThat(vo.getStatus()).isEqualTo("running");
                assertThat(vo.getSeckillGoodsList()).hasSize(1);
                assertThat(vo.getSeckillGoodsList().get(0).getSeckillGoodsId()).isEqualTo(100L);
            }

            @Test
            @DisplayName("D2 活动不存在 → BusinessException")
            void notFound() {
                when(activityMapper.selectById(qActivityId)).thenReturn(null);

                assertThatThrownBy(() -> activityService.getActivityDetail(qActivityId))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("不存在");
            }

            @Test
            @DisplayName("D3 无秒杀商品 → seckillGoodsList=[]")
            void noGoods() {
                Activity activity = qActivity(qActivityId, qMerchantId, ActivityStatus.draft);
                when(activityMapper.selectById(qActivityId)).thenReturn(activity);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of());

                ActivityVO vo = activityService.getActivityDetail(qActivityId);

                assertThat(vo.getSeckillGoodsList()).isEmpty();
            }

            @Test
            @DisplayName("D4 多件秒杀商品 → 全部返回")
            void multipleGoods() {
                Activity activity = qActivity(qActivityId, qMerchantId, ActivityStatus.preheating);
                when(activityMapper.selectById(qActivityId)).thenReturn(activity);

                SeckillGoods sg1 = seckillGoods(100L, 200L, new BigDecimal("9.90"), 50, 1);
                SeckillGoods sg2 = seckillGoods(101L, 201L, new BigDecimal("19.90"), 30, 2);
                when(seckillGoodsMapper.selectList(any())).thenReturn(List.of(sg1, sg2));

                ActivityVO vo = activityService.getActivityDetail(qActivityId);

                assertThat(vo.getSeckillGoodsList()).hasSize(2);
            }
        }

        // ================================================================
        // listMerchantActivities
        // ================================================================

        @Nested
        @DisplayName("listMerchantActivities")
        class ListMerchantActivities {

            @Test
            @DisplayName("M1 3条数据 page=1 → 返回3条 total=3")
            void happyPath() {
                List<Activity> activities = List.of(
                        qActivity(1L, qMerchantId, ActivityStatus.draft),
                        qActivity(2L, qMerchantId, ActivityStatus.pending),
                        qActivity(3L, qMerchantId, ActivityStatus.running));
                Page<Activity> mpPage = new Page<>(1, 10);
                mpPage.setRecords(activities);
                mpPage.setTotal(3);
                when(activityMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

                PageVO<ActivityVO> result = activityService.listMerchantActivities(qMerchantId, 1, 10);

                assertThat(result.getRecords()).hasSize(3);
                assertThat(result.getTotal()).isEqualTo(3);
                assertThat(result.getPage()).isEqualTo(1);
                assertThat(result.getPageSize()).isEqualTo(10);
                assertThat(result.getRecords().get(0).getActivityId()).isEqualTo(1L);
                assertThat(result.getRecords().get(0).getStatus()).isEqualTo("draft");
                assertThat(result.getRecords().get(1).getStatus()).isEqualTo("pending");
                assertThat(result.getRecords().get(2).getStatus()).isEqualTo("running");
            }

            @Test
            @DisplayName("M2 空结果 → records=[] total=0")
            void emptyResult() {
                Page<Activity> mpPage = new Page<>(1, 10);
                mpPage.setRecords(List.of());
                mpPage.setTotal(0);
                when(activityMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

                PageVO<ActivityVO> result = activityService.listMerchantActivities(qMerchantId, 1, 10);

                assertThat(result.getRecords()).isEmpty();
                assertThat(result.getTotal()).isZero();
            }

            @Test
            @DisplayName("M3 wrapper 含 merchant_id 和 created_at 排序")
            void wrapperConditions() {
                Page<Activity> mpPage = new Page<>(1, 10);
                mpPage.setRecords(List.of());
                mpPage.setTotal(0);
                when(activityMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

                activityService.listMerchantActivities(qMerchantId, 1, 10);

                @SuppressWarnings("unchecked")
                var wrapperCaptor = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
                verify(activityMapper).selectPage(any(Page.class), wrapperCaptor.capture());
                String sql = wrapperCaptor.getValue().getCustomSqlSegment();
                assertThat(sql).containsIgnoringCase("merchant_id")
                        .containsIgnoringCase("created_at");
            }

            @Test
            @DisplayName("M4 分页参数透传 page=2 pageSize=5")
            void pageSizePassing() {
                Page<Activity> mpPage = new Page<>(2, 5);
                mpPage.setRecords(List.of(qActivity(6L, qMerchantId, ActivityStatus.draft)));
                mpPage.setTotal(6);
                when(activityMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

                activityService.listMerchantActivities(qMerchantId, 2, 5);

                @SuppressWarnings("unchecked")
                var pageCaptor = ArgumentCaptor.forClass(Page.class);
                verify(activityMapper).selectPage(pageCaptor.capture(), any());
                assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2);
                assertThat(pageCaptor.getValue().getSize()).isEqualTo(5);
            }
        }

        // ================================================================
        // listAllActivities
        // ================================================================

        @Nested
        @DisplayName("listAllActivities")
        class ListAllActivities {

            @Test
            @DisplayName("A1 管理员查看全部 → 所有状态")
            void happyPath() {
                Activity a1 = qActivity(1L, 1L, ActivityStatus.draft);
                Activity a2 = qActivity(2L, 2L, ActivityStatus.ended);
                Page<Activity> mpPage = new Page<>(1, 10);
                mpPage.setRecords(List.of(a1, a2));
                mpPage.setTotal(2);
                when(activityMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

                PageVO<ActivityVO> result = activityService.listAllActivities(1, 10);

                assertThat(result.getRecords()).hasSize(2);
                assertThat(result.getTotal()).isEqualTo(2);
            }

            @Test
            @DisplayName("A2 空结果 → records=[] total=0")
            void emptyResult() {
                Page<Activity> mpPage = new Page<>(1, 10);
                mpPage.setRecords(List.of());
                mpPage.setTotal(0);
                when(activityMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

                PageVO<ActivityVO> result = activityService.listAllActivities(1, 10);

                assertThat(result.getRecords()).isEmpty();
                assertThat(result.getTotal()).isZero();
            }
        }

        // ================================================================
        // listActiveActivities
        // ================================================================

        @Nested
        @DisplayName("listActiveActivities")
        class ListActiveActivities {

            @Test
            @DisplayName("U1 只返回 preheating/running/ended")
            void onlyActiveStatuses() {
                Page<Activity> mpPage = new Page<>(1, 10);
                mpPage.setRecords(List.of(
                        qActivity(1L, 1L, ActivityStatus.preheating),
                        qActivity(2L, 1L, ActivityStatus.running),
                        qActivity(3L, 1L, ActivityStatus.ended)));
                mpPage.setTotal(3);
                when(activityMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

                PageVO<ActivityVO> result = activityService.listActiveActivities(1, 10);

                assertThat(result.getRecords()).hasSize(3);
            }

            @Test
            @DisplayName("U2 无符合状态的活动 → records=[] total=0")
            void emptyResult() {
                Page<Activity> mpPage = new Page<>(1, 10);
                mpPage.setRecords(List.of());
                mpPage.setTotal(0);
                when(activityMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

                PageVO<ActivityVO> result = activityService.listActiveActivities(1, 10);

                assertThat(result.getRecords()).isEmpty();
            }

            @Test
            @DisplayName("U3 wrapper 限 preheating/running/ended，不含 draft/pending")
            void wrapperStatusFilter() {
                Page<Activity> mpPage = new Page<>(1, 10);
                mpPage.setRecords(List.of());
                mpPage.setTotal(0);
                when(activityMapper.selectPage(any(Page.class), any())).thenReturn(mpPage);

                activityService.listActiveActivities(1, 10);

                @SuppressWarnings("unchecked")
                var wrapperCaptor = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
                verify(activityMapper).selectPage(any(Page.class), wrapperCaptor.capture());
                String sql = wrapperCaptor.getValue().getCustomSqlSegment();
                // 参数化为 ?，不包含具体枚举名，检查 column name + IN 关键字
                assertThat(sql).containsIgnoringCase("status").contains("IN");
                // 不该含其他状态的 column 引用
                assertThat(sql).doesNotContain("draft").doesNotContain("pending");
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
