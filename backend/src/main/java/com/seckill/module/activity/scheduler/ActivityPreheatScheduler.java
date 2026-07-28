package com.seckill.module.activity.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.module.activity.mapper.ActivityMapper;
import com.seckill.module.activity.model.entity.Activity;
import com.seckill.module.activity.model.enums.ActivityStatus;
import com.seckill.module.activity.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动预热定时任务。
 *
 * <p>每分钟扫描 {@code preheating} 状态的活动：</p>
 * <ul>
 *   <li>距开始 ≤10 分钟 → 预热库存 + 黑名单到 Redis（幂等）</li>
 *   <li>已到开始时间 → 转换 {@code preheating → running}</li>
 * </ul>
 *
 * <p>单个活动失败不影响其他活动。</p>
 */
@Component
@RequiredArgsConstructor
public class ActivityPreheatScheduler {

    private static final Logger log = LoggerFactory.getLogger(ActivityPreheatScheduler.class);

    private final ActivityMapper activityMapper;
    private final ActivityService activityService;

    @Scheduled(fixedRate = 60000)
    public void preheatAndStart() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusMinutes(10);

        List<Activity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, ActivityStatus.preheating)
                        .le(Activity::getStartTime, deadline));

        for (Activity a : activities) {
            try {
                activityService.preheatActivity(a.getActivityId());

                if (!a.getStartTime().isAfter(now)) {
                    activityService.startActivity(a.getActivityId());
                    log.info("Activity {} started", a.getActivityId());
                }
            } catch (Exception e) {
                log.error("Failed to process activity {}", a.getActivityId(), e);
            }
        }
    }
}
