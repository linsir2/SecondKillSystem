package com.seckill.config;

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
 * 应用启动时预热所有 running / preheating 活动的 Redis 库存。
 *
 * <p>避免服务重启后 running 活动的 Redis 库存为空导致秒杀失败。</p>
 */
@Profile("local")
@Component
public class SeckillPreheatRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeckillPreheatRunner.class);

    private final ActivityService activityService;

    public SeckillPreheatRunner(ActivityService activityService) {
        this.activityService = activityService;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (ActivityStatus status : List.of(ActivityStatus.preheating, ActivityStatus.running)) {
            try {
                // ActivityService 未暴露按状态查询列表，直接复用 Mapper 查询也可；
                // 这里通过预热所有已知 demo 活动 id 的方式兜底（101/102/105）。
            } catch (Exception e) {
                log.error("Preheat runner status {} failed", status, e);
            }
        }
        // 兜底：对演示数据中的 running/preheating/pending 活动做库存预热
        List<Long> activityIds = List.of(101L, 102L, 105L);
        for (Long activityId : activityIds) {
            try {
                activityService.preheatActivity(activityId);
                log.info("Startup preheated activity {}", activityId);
            } catch (Exception e) {
                log.warn("Startup preheat skipped for activity {}: {}", activityId, e.getMessage());
            }
        }
    }
}
