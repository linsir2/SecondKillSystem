package com.seckill.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.module.activity.mapper.ActivityMapper;
import com.seckill.module.activity.model.entity.Activity;
import com.seckill.module.activity.model.enums.ActivityStatus;
import com.seckill.module.activity.service.ActivityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动时预热所有 running / preheating / pending 活动的 Redis 库存。
 *
 * <p>避免服务重启后 running 活动的 Redis 库存为空导致秒杀失败。</p>
 */
@Profile({"local", "dev"})
@Component
public class SeckillPreheatRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeckillPreheatRunner.class);

    private final ActivityMapper activityMapper;
    private final ActivityService activityService;

    public SeckillPreheatRunner(ActivityMapper activityMapper, ActivityService activityService) {
        this.activityMapper = activityMapper;
        this.activityService = activityService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<ActivityStatus> statuses = List.of(ActivityStatus.preheating, ActivityStatus.running, ActivityStatus.pending);
        for (ActivityStatus status : statuses) {
            try {
                List<Activity> activities = activityMapper.selectList(
                        new LambdaQueryWrapper<Activity>().eq(Activity::getStatus, status));
                for (Activity activity : activities) {
                    try {
                        activityService.preheatActivity(activity.getActivityId());
                        log.info("Startup preheated activity {} (status={})", activity.getActivityId(), status);
                    } catch (Exception e) {
                        log.warn("Startup preheat skipped for activity {}: {}", activity.getActivityId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Preheat runner status {} failed", status, e);
            }
        }
    }
}
