package com.seckill.module.activity.model.entity;

import com.seckill.module.activity.model.enums.ActivityStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link Activity} 实体领域方法 — end()。
 *
 * <p>纯 POJO 测试，无 Mock。</p>
 *
 * <pre>
 * E1  running → ended
 * E2-E5  4 种非 running 状态 → IllegalStateException
 * E6  status=null → IllegalStateException
 * </pre>
 */
@DisplayName("Activity 实体 — end()")
class ActivityTest {

    private Activity activityWithStatus(ActivityStatus status) {
        Activity a = new Activity();
        a.setStatus(status);
        return a;
    }

    @Test
    @DisplayName("E1 running → ended")
    void runningToEnded() {
        Activity a = activityWithStatus(ActivityStatus.running);
        a.end();
        assertEquals(ActivityStatus.ended, a.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = ActivityStatus.class, names = {"draft", "pending", "preheating", "ended"})
    @DisplayName("E2-E5 非 running → IllegalStateException")
    void notRunningThrows(ActivityStatus status) {
        Activity a = activityWithStatus(status);
        assertThrows(IllegalStateException.class, a::end);
    }

    @Test
    @DisplayName("E6 status=null → IllegalStateException")
    void nullStatus() {
        Activity a = new Activity();
        assertThrows(IllegalStateException.class, a::end);
    }

    // ========================================================================
    // reject()
    // ========================================================================

    @Nested
    @DisplayName("Activity 实体 — reject()")
    class Reject {

        @Test
        @DisplayName("R1 pending → draft, rejectReason 设值")
        void pendingToDraft() {
            Activity a = activityWithStatus(ActivityStatus.pending);
            a.reject("价格不合理");

            assertEquals(ActivityStatus.draft, a.getStatus());
            assertEquals("价格不合理", a.getRejectReason());
        }

        @Test
        @DisplayName("R2 rejectReason 为 null → 允许设 null")
        void nullReason() {
            Activity a = activityWithStatus(ActivityStatus.pending);
            a.reject(null);

            assertEquals(ActivityStatus.draft, a.getStatus());
            assertNull(a.getRejectReason());
        }

        @ParameterizedTest
        @EnumSource(value = ActivityStatus.class, names = {"draft", "preheating", "running", "ended"})
        @DisplayName("R3-R6 非 pending → IllegalStateException")
        void notPendingThrows(ActivityStatus status) {
            Activity a = activityWithStatus(status);
            assertThrows(IllegalStateException.class, () -> a.reject("任何理由"));
        }

        @Test
        @DisplayName("R7 status=null → IllegalStateException")
        void nullStatus() {
            Activity a = new Activity();
            assertThrows(IllegalStateException.class, () -> a.reject("理由"));
        }
    }
}
