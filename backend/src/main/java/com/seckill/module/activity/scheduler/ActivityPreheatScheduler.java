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
 * <p>每分钟扫描 {@code preheating} / {@code running} 状态的活动：</p>
 * <ul>
 *   <li>距开始 ≤10 分钟 → 预热库存 + 限购到 Redis（幂等）</li>
 *   <li>已到开始时间 → 转换 {@code preheating → running}</li>
 *   <li>对 {@code running} 活动兜底预热，防止服务重启后 Redis 库存丢失</li>
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
    public void processActivities() {
        LocalDateTime now = LocalDateTime.now();
        phase1_preheatAndStart(now);
        phase1b_reheatRunning(now);
        phase2_endExpired(now);
        phase3_restoreStock(now);
    }

    private void phase1_preheatAndStart(LocalDateTime now) {
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

    /**
     * 兜底：对 running 活动做幂等预热，避免服务重启后 Redis 库存为空导致秒杀失败。
     */
    private void phase1b_reheatRunning(LocalDateTime now) {
        List<Activity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, ActivityStatus.running));

        for (Activity a : activities) {
            try {
                activityService.preheatActivity(a.getActivityId());
            } catch (Exception e) {
                log.error("Failed to reheat running activity {}", a.getActivityId(), e);
            }
        }
    }

    private void phase2_endExpired(LocalDateTime now) {
        List<Activity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, ActivityStatus.running)
                        .le(Activity::getEndTime, now));

        for (Activity a : activities) {
            try {
                activityService.endActivity(a.getActivityId());
                log.info("Activity {} ended", a.getActivityId());
            } catch (Exception e) {
                log.error("Failed to end activity {}", a.getActivityId(), e);
            }
        }
    }

    private void phase3_restoreStock(LocalDateTime now) {
        LocalDateTime restoreDeadline = now.minusMinutes(4);

        List<Activity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .eq(Activity::getStatus, ActivityStatus.ended)
                        .le(Activity::getEndTime, restoreDeadline));

        for (Activity a : activities) {
            try {
                activityService.restoreActivityStock(a.getActivityId());
                log.info("Activity {} stock restored", a.getActivityId());
            } catch (Exception e) {
                log.error("Failed to restore stock for activity {}", a.getActivityId(), e);
            }
        }
    }
}
