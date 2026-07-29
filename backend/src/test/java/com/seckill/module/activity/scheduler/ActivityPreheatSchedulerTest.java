package com.seckill.module.activity.scheduler;

import com.seckill.module.activity.mapper.ActivityMapper;
import com.seckill.module.activity.model.entity.Activity;
import com.seckill.module.activity.model.enums.ActivityStatus;
import com.seckill.module.activity.service.ActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityPreheatScheduler")
class ActivityPreheatSchedulerTest {

    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private ActivityService activityService;

    private ActivityPreheatScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ActivityPreheatScheduler(activityMapper, activityService);
    }

    private Activity anActivity(Long id, LocalDateTime startTime) {
        Activity a = new Activity();
        a.setActivityId(id);
        a.setStatus(ActivityStatus.preheating);
        a.setStartTime(startTime);
        return a;
    }

    @Nested
    @DisplayName("preheatAndStart")
    class PreheatAndStart {

        @Test
        @DisplayName("到开始时间 → preheat + start 都被调")
        void startTimeArrived() {
            Activity a = anActivity(10L, LocalDateTime.now().minusSeconds(30));
            when(activityMapper.selectList(any())).thenReturn(List.of(a));

            scheduler.processActivities();

            verify(activityService).preheatActivity(10L);
            verify(activityService).startActivity(10L);
        }

        @Test
        @DisplayName("距开始还有 5 分钟 → 只 preheat，不 start")
        void notYetStarted() {
            Activity a = anActivity(10L, LocalDateTime.now().plusMinutes(5));
            when(activityMapper.selectList(any())).thenReturn(List.of(a));

            scheduler.processActivities();

            verify(activityService).preheatActivity(10L);
            verify(activityService, never()).startActivity(10L);
        }

        @Test
        @DisplayName("无待预热的活动 → 无事发生")
        void nothingToPreheat() {
            when(activityMapper.selectList(any())).thenReturn(List.of());

            scheduler.processActivities();

            verify(activityService, never()).preheatActivity(any());
            verify(activityService, never()).startActivity(any());
        }

        @Test
        @DisplayName("第一个活动失败 → 不影响第二个")
        void firstFailsSecondStillProcessed() {
            Activity a1 = anActivity(10L, LocalDateTime.now().minusSeconds(30));
            Activity a2 = anActivity(20L, LocalDateTime.now().minusSeconds(30));
            when(activityMapper.selectList(any())).thenReturn(List.of(a1, a2));

            doThrow(new RuntimeException("DB error")).when(activityService).preheatActivity(10L);

            scheduler.processActivities();

            verify(activityService).preheatActivity(20L);
            verify(activityService).startActivity(20L);
        }
    }
}
