package com.seckill.module.message.controller;

import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.message.model.entity.UserMessage;
import com.seckill.module.message.model.enums.MessageType;
import com.seckill.module.message.model.vo.MessageVO;
import com.seckill.module.message.service.UserMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageController")
class MessageControllerTest {

    @Mock
    private UserMessageService userMessageService;

    @InjectMocks
    private MessageController controller;

    private final CurrentUser user = new CurrentUser(100L, "买家A", null);

    @BeforeEach
    void clearSecurityContext() {
        SecurityContext.clear();
    }

    // ========================================================================
    // GET /api/v1/messages
    // ========================================================================

    @Nested
    @DisplayName("GET /api/v1/messages")
    class ListMessages {

        @Test
        @DisplayName("未登录 → BusinessException")
        void notLoggedIn() {
            assertThatThrownBy(() -> controller.listMessages(1, 10))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未登录");
            verify(userMessageService, never()).listUserMessages(anyLong(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("有消息 → 200 + VO 列表")
        void hasMessages() {
            SecurityContext.set(user);
            var entity = new UserMessage();
            entity.setMessageId(1L);
            entity.setUserId(100L);
            entity.setMsgType(MessageType.welcome);
            entity.setContent("欢迎！");
            entity.setActivityId(null);
            entity.setIsRead(false);
            entity.setCreatedAt(LocalDateTime.of(2026, 7, 29, 12, 0));
            when(userMessageService.listUserMessages(100L, 1, 10)).thenReturn(List.of(entity));

            Result<List<MessageVO>> result = controller.listMessages(1, 10);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).hasSize(1);
            MessageVO vo = result.getData().get(0);
            assertThat(vo.messageId()).isEqualTo(1L);
            assertThat(vo.type()).isEqualTo("welcome");
            assertThat(vo.content()).isEqualTo("欢迎！");
            assertThat(vo.activityId()).isNull();
            assertThat(vo.read()).isFalse();
        }

        @Test
        @DisplayName("无消息 → 200 + 空数组")
        void emptyMessages() {
            SecurityContext.set(user);
            when(userMessageService.listUserMessages(100L, 1, 10)).thenReturn(List.of());

            Result<List<MessageVO>> result = controller.listMessages(1, 10);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isEmpty();
        }

        @Test
        @DisplayName("传 page=2, size=10 → 透传")
        void customPage() {
            SecurityContext.set(user);
            when(userMessageService.listUserMessages(100L, 2, 10)).thenReturn(List.of());

            controller.listMessages(2, 10);

            verify(userMessageService).listUserMessages(100L, 2, 10);
        }

        @Test
        @DisplayName("不传分页参数 → 默认 (1, 20)")
        void defaultPage() {
            SecurityContext.set(user);
            when(userMessageService.listUserMessages(100L, 1, 20)).thenReturn(List.of());

            controller.listMessages(null, null);

            verify(userMessageService).listUserMessages(100L, 1, 20);
        }
    }

    // ========================================================================
    // GET /api/v1/messages/unread/count
    // ========================================================================

    @Nested
    @DisplayName("GET /api/v1/messages/unread/count")
    class CountUnread {

        @Test
        @DisplayName("未登录 → BusinessException")
        void notLoggedIn() {
            assertThatThrownBy(() -> controller.countUnread())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未登录");
            verify(userMessageService, never()).countUnread(anyLong());
        }

        @Test
        @DisplayName("有 5 条未读 → 200 + data=5")
        void hasUnread() {
            SecurityContext.set(user);
            when(userMessageService.countUnread(100L)).thenReturn(5L);

            Result<Long> result = controller.countUnread();

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isEqualTo(5);
        }

        @Test
        @DisplayName("全部已读 → 200 + data=0")
        void allRead() {
            SecurityContext.set(user);
            when(userMessageService.countUnread(100L)).thenReturn(0L);

            Result<Long> result = controller.countUnread();

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isZero();
        }
    }

    // ========================================================================
    // PUT /api/v1/messages/{messageId}/read
    // ========================================================================

    @Nested
    @DisplayName("PUT /api/v1/messages/{messageId}/read")
    class MarkAsRead {

        @Test
        @DisplayName("未登录 → BusinessException")
        void notLoggedIn() {
            assertThatThrownBy(() -> controller.markAsRead(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未登录");
            verify(userMessageService, never()).markAsRead(anyLong(), anyLong());
        }

        @Test
        @DisplayName("标记自己的消息 → 200")
        void success() {
            SecurityContext.set(user);

            Result<Void> result = controller.markAsRead(1L);

            assertThat(result.getCode()).isEqualTo(200);
            verify(userMessageService).markAsRead(1L, 100L);
        }
    }
}
