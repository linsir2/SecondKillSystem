package com.seckill.module.activity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seckill.common.constant.BanStatus;
import com.seckill.common.exception.BusinessException;
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
import com.seckill.module.activity.model.vo.SeckillGoodsVO;
import com.seckill.module.goods.model.dto.GoodsInfo;
import com.seckill.module.goods.service.GoodsService;
import com.seckill.module.user.mapper.SysUserMapper;
import com.seckill.module.user.model.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 活动领域服务 —— 草稿创建。
 *
 * <p>草稿阶段只做本地校验（不调远程），实时校验（商品存在性、库存上限）留在提交审核时处理。
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityMapper activityMapper;
    private final SeckillGoodsMapper seckillGoodsMapper;
    private final GoodsService goodsService;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final SysUserMapper sysUserMapper;

    // ========================================================================
    // 创建草稿
    // ========================================================================

    /**
     * 商家创建秒杀活动草稿。
     * <p>仅做本地校验：字段格式、不重复的 goodsId、非负数值。不调远程服务。</p>
     *
     * @param merchantId 商家 ID
     * @param request    创建请求
     * @return 活动草稿详情
     */
    @Transactional
    public ActivityVO createActivity(Long merchantId, CreateActivityRequest request) {
        validateRequest(request);

        Activity activity = buildActivity(merchantId, request);
        activityMapper.insert(activity);

        List<SeckillGoodsVO> goodsVOs = insertSeckillGoods(activity.getActivityId(),
                request.getSeckillGoodsList());

        return buildVO(activity, goodsVOs);
    }

    // ========================================================================
    // 校验
    // ========================================================================

    private void validateRequest(CreateActivityRequest request) {
        // 活动名
        if (request.getActivityName() == null || request.getActivityName().isBlank()) {
            throw new IllegalArgumentException("活动名不能为空");
        }
        if (request.getActivityName().length() > 255) {
            throw new IllegalArgumentException("活动名不能超过255个字符");
        }

        // 时间窗口（仅格式校验，不检查是否过去）
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("结束时间必须在开始时间之后");
        }

        // 秒杀商品列表
        List<CreateSeckillGoodsItem> items = request.getSeckillGoodsList();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("至少需要一件秒杀商品");
        }

        // 重复 goodsId + 字段值校验
        var goodsIds = new HashSet<Long>();
        for (CreateSeckillGoodsItem item : items) {
            if (!goodsIds.add(item.getGoodsId())) {
                throw new BusinessException("秒杀商品列表中存在重复的商品 ID: " + item.getGoodsId());
            }
            validateItemFields(item);
        }
    }

    private void validateItemFields(CreateSeckillGoodsItem item) {
        if (item.getSeckillPrice() == null || item.getSeckillPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("秒杀价必须大于0");
        }
        if (item.getStock() == null || item.getStock() <= 0) {
            throw new IllegalArgumentException("秒杀库存必须大于0");
        }
        if (item.getLimitNum() == null || item.getLimitNum() <= 0) {
            throw new IllegalArgumentException("限购数必须大于0");
        }
    }

    // ========================================================================
    // 写入
    // ========================================================================

    private Activity buildActivity(Long merchantId, CreateActivityRequest request) {
        Activity activity = new Activity();
        activity.setActivityName(request.getActivityName());
        activity.setMerchantId(merchantId);
        activity.setStatus(ActivityStatus.draft);
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setDescription(request.getDescription());
        return activity;
    }

    private List<SeckillGoodsVO> insertSeckillGoods(Long activityId,
                                                     List<CreateSeckillGoodsItem> items) {
        List<SeckillGoodsVO> vos = new ArrayList<>(items.size());

        for (CreateSeckillGoodsItem item : items) {
            SeckillGoods sg = new SeckillGoods();
            sg.setActivityId(activityId);
            sg.setGoodsId(item.getGoodsId());
            sg.setSeckillPrice(item.getSeckillPrice());
            sg.setStock(item.getStock());
            sg.setLimitNum(item.getLimitNum());

            seckillGoodsMapper.insert(sg);

            vos.add(new SeckillGoodsVO(
                    sg.getSeckillGoodsId(),
                    item.getGoodsId(),
                    null, // 草稿阶段无 goodsName
                    item.getSeckillPrice(),
                    item.getStock(),
                    item.getLimitNum()
            ));
        }

        return vos;
    }

    // ========================================================================
    // 组装 VO
    // ========================================================================

    private ActivityVO buildVO(Activity activity, List<SeckillGoodsVO> goodsVOs) {
        return new ActivityVO(
                activity.getActivityId(),
                activity.getActivityName(),
                activity.getMerchantId(),
                activity.getStatus().name(),
                activity.getStartTime(),
                activity.getEndTime(),
                activity.getDescription(),
                goodsVOs,
                activity.getCreatedAt()
        );
    }

    // ========================================================================
    // 提交审核
    // ========================================================================

    /**
     * 商家提交秒杀活动审核。
     * <p>前置条件失败时 fail-fast，商品级校验收集全部问题后统一抛。</p>
     *
     * @param merchantId 商家 ID
     * @param activityId 活动 ID
     * @return 活动详情（goodsName 已填充）
     */
    @Transactional
    public ActivityVO submitForReview(Long merchantId, Long activityId) {
        // ---- 前置校验（fail-fast） ----
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        if (!activity.getMerchantId().equals(merchantId)) {
            throw new BusinessException("无权操作该活动");
        }
        if (!activity.getStartTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("活动开始时间已过期");
        }

        // ---- 商品校验（收集全部错误） ----
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(
                new LambdaQueryWrapper<SeckillGoods>()
                        .eq(SeckillGoods::getActivityId, activityId));

        List<String> errors = new ArrayList<>();
        List<GoodsInfo> goodsInfos = Collections.emptyList();

        if (seckillGoodsList.isEmpty()) {
            errors.add("该活动未绑定任何秒杀商品");
        } else {
            List<Long> goodsIds = seckillGoodsList.stream()
                    .map(SeckillGoods::getGoodsId)
                    .toList();
            goodsInfos = goodsService.getGoodsInfoList(goodsIds, merchantId);

            // 构建 goodsId → GoodsInfo 映射
            Map<Long, GoodsInfo> infoMap = new HashMap<>();
            for (GoodsInfo info : goodsInfos) {
                if (info != null) {
                    infoMap.put(info.getGoodsId(), info);
                }
            }

            for (SeckillGoods sg : seckillGoodsList) {
                GoodsInfo info = infoMap.get(sg.getGoodsId());
                if (info == null) {
                    errors.add("商品 ID " + sg.getGoodsId() + " 不存在或不属于您");
                } else if (info.getStock() != null && sg.getStock() > info.getStock()) {
                    errors.add("《" + info.getGoodsName() + "》秒杀库存(" + sg.getStock()
                            + ")超过日常库存(" + info.getStock() + ")");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException("部分商品校验未通过", errors);
        }

        // ---- 实体领域行为 + 乐观锁更新 ----
        try {
            activity.submitForReview();
        } catch (IllegalStateException e) {
            throw new BusinessException(e.getMessage());
        }
        LambdaUpdateWrapper<Activity> wrapper = new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getActivityId, activityId)
                .eq(Activity::getStatus, ActivityStatus.draft);
        int affected = activityMapper.update(activity, wrapper);
        if (affected == 0) {
            throw new BusinessException("提交失败，请刷新重试");
        }

        // ---- 领域事件 ----
        eventPublisher.publishEvent(new ActivitySubmittedForReviewEvent(activityId, merchantId));

        // ---- 组装 VO ----
        List<SeckillGoodsVO> goodsVOs = buildSubmitGoodsVOs(seckillGoodsList, goodsInfos);
        return buildVO(activity, goodsVOs);
    }

    private List<SeckillGoodsVO> buildSubmitGoodsVOs(List<SeckillGoods> seckillGoodsList,
                                                      List<GoodsInfo> goodsInfos) {
        Map<Long, GoodsInfo> infoMap = new HashMap<>();
        for (GoodsInfo info : goodsInfos) {
            if (info != null) {
                infoMap.put(info.getGoodsId(), info);
            }
        }

        List<SeckillGoodsVO> vos = new ArrayList<>(seckillGoodsList.size());
        for (SeckillGoods sg : seckillGoodsList) {
            GoodsInfo info = infoMap.get(sg.getGoodsId());
            vos.add(new SeckillGoodsVO(
                    sg.getSeckillGoodsId(),
                    sg.getGoodsId(),
                    info != null ? info.getGoodsName() : null,
                    sg.getSeckillPrice(),
                    sg.getStock(),
                    sg.getLimitNum()
            ));
        }
        return vos;
    }

    // ========================================================================
    // 审核通过
    // ========================================================================

    /**
     * 管理员审核通过秒杀活动（pending → preheating）。
     *
     * <p>流程：前置校验 → 商品校验 → goods 预占库存 → 乐观锁更新状态 → 返回 VO</p>
     *
     * @param activityId 活动 ID
     * @return 活动详情（status=preheating，goodsName 已填充）
     */
    @Transactional
    public ActivityVO approveActivity(Long activityId) {
        // ---- 前置校验 ----
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        if (!activity.getStartTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("活动开始时间已过期");
        }

        // ---- 商品校验（收集全部错误） ----
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(
                new LambdaQueryWrapper<SeckillGoods>()
                        .eq(SeckillGoods::getActivityId, activityId));

        List<Long> goodsIds = seckillGoodsList.stream()
                .map(SeckillGoods::getGoodsId)
                .toList();
        List<GoodsInfo> goodsInfos = goodsService.getGoodsInfoList(goodsIds, activity.getMerchantId());

        Map<Long, GoodsInfo> infoMap = new HashMap<>();
        for (GoodsInfo info : goodsInfos) {
            if (info != null) {
                infoMap.put(info.getGoodsId(), info);
            }
        }

        List<String> errors = new ArrayList<>();
        for (SeckillGoods sg : seckillGoodsList) {
            GoodsInfo info = infoMap.get(sg.getGoodsId());
            if (info == null) {
                errors.add("商品 ID " + sg.getGoodsId() + " 不存在或不属于您");
            } else if (info.getStock() < sg.getStock()) {
                errors.add("《" + info.getGoodsName() + "》库存不足，需 "
                        + sg.getStock() + " 可用 " + info.getStock());
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("部分商品校验未通过", errors);
        }

        // ---- 实体领域行为（先校验，再预占库存） ----
        try {
            activity.approve();
            for (SeckillGoods sg : seckillGoodsList) {
                goodsService.deductStock(sg.getGoodsId(), activity.getMerchantId(), sg.getStock());
            }
        } catch (IllegalStateException e) {
            throw new BusinessException(e.getMessage());
        }

        // ---- 乐观锁更新 ----
        LambdaUpdateWrapper<Activity> wrapper = new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getActivityId, activityId)
                .eq(Activity::getStatus, ActivityStatus.pending);
        int affected = activityMapper.update(activity, wrapper);
        if (affected == 0) {
            throw new BusinessException("审核失败，请刷新重试");
        }

        // ---- 领域事件 ----
        eventPublisher.publishEvent(new ActivityApprovedEvent(activityId, activity.getMerchantId()));

        // ---- 组装 VO ----
        List<SeckillGoodsVO> goodsVOs = buildSubmitGoodsVOs(seckillGoodsList, goodsInfos);
        return buildVO(activity, goodsVOs);
    }

    // ========================================================================
    // 预热（T-10 分钟定时任务触发）
    // ========================================================================

    private static final String BLACKLIST_KEY = "seckill:blacklist";

    /**
     * 预热活动数据到 Redis：库存 key + 限购 key + 黑名单。
     * <p>幂等，可重复调用。活动状态不变（仍为 preheating）。</p>
     */
    public void preheatActivity(Long activityId) {
        // ---- 库存预热 ----
        List<SeckillGoods> sgList = seckillGoodsMapper.selectList(
                new LambdaQueryWrapper<SeckillGoods>().eq(SeckillGoods::getActivityId, activityId));
        for (SeckillGoods sg : sgList) {
            String stockKey = redisKey("stock", activityId, sg.getSeckillGoodsId());
            String limitKey = redisKey("limit", activityId, sg.getSeckillGoodsId());
            redisTemplate.opsForValue().set(stockKey, String.valueOf(sg.getStock()));
            redisTemplate.opsForValue().set(limitKey, String.valueOf(sg.getLimitNum()));
        }

        // ---- 黑名单预热 ----
        List<SysUser> banned = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getBanStatus, BanStatus.banned));
        redisTemplate.delete(BLACKLIST_KEY);
        for (SysUser user : banned) {
            redisTemplate.opsForSet().add(BLACKLIST_KEY, String.valueOf(user.getUserId()));
        }
    }

    // ========================================================================
    // 启动活动（start_time 到达时触发）
    // ========================================================================

    /**
     * preheating → running。
     * <p>乐观锁防止重复转换，状态不匹配时幂等跳过。</p>
     */
    @Transactional
    public void startActivity(Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) return;
        if (activity.getStatus() != ActivityStatus.preheating) return;

        activity.start();

        LambdaUpdateWrapper<Activity> wrapper = new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getActivityId, activityId)
                .eq(Activity::getStatus, ActivityStatus.preheating);
        int affected = activityMapper.update(activity, wrapper);
        if (affected == 0) {
            throw new BusinessException("活动启动失败，请刷新重试");
        }
    }

    private static String redisKey(String prefix, Long activityId, Long seckillGoodsId) {
        return "seckill:" + prefix + ":" + activityId + ":" + seckillGoodsId;
    }
}
