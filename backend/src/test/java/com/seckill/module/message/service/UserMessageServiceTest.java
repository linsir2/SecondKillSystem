package com.seckill.module.message.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.module.message.mapper.UserMessageMapper;
import com.seckill.module.message.model.entity.UserMessage;
import com.seckill.module.message.model.enums.MessageType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserMessageServiceTest {

    @Mock
    private UserMessageMapper userMessageMapper;

    @InjectMocks
    private UserMessageServiceImpl userMessageService;

    @Captor
    private ArgumentCaptor<UserMessage> msgCaptor;

    // ========================================================================
    // sendMessage
    // ========================================================================

    @Nested
    class SendMessage {

        @Test
        void happyPath() {
            userMessageService.sendMessage(100L, MessageType.approval_result, "您的活动已通过审核", 1L);

            verify(userMessageMapper).insert(msgCaptor.capture());
            UserMessage msg = msgCaptor.getValue();
            assertThat(msg.getUserId()).isEqualTo(100L);
            assertThat(msg.getMsgType()).isEqualTo(MessageType.approval_result);
            assertThat(msg.getContent()).isEqualTo("您的活动已通过审核");
            assertThat(msg.getActivityId()).isEqualTo(1L);
        }

        @Test
        void activityId_nullable() {
            userMessageService.sendMessage(100L, MessageType.approval_result, "内容", null);

            verify(userMessageMapper).insert(msgCaptor.capture());
            assertThat(msgCaptor.getValue().getActivityId()).isNull();
        }

        @Test
        void nullUserId_throws() {
            assertThatThrownBy(() -> userMessageService.sendMessage(null, MessageType.approval_result, "内容", 1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullMsgType_throws() {
            assertThatThrownBy(() -> userMessageService.sendMessage(100L, null, "内容", 1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullContent_throws() {
            assertThatThrownBy(() -> userMessageService.sendMessage(100L, MessageType.approval_result, null, 1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void isRead_defaultsToFalse() {
            userMessageService.sendMessage(100L, MessageType.approval_result, "内容", 1L);

            verify(userMessageMapper).insert(msgCaptor.capture());
            assertThat(msgCaptor.getValue().getIsRead()).isFalse();
        }

        @Test
        void createdAt_autoSet() {
            userMessageService.sendMessage(100L, MessageType.approval_result, "内容", 1L);

            verify(userMessageMapper).insert(msgCaptor.capture());
            assertThat(msgCaptor.getValue().getCreatedAt()).isNotNull();
        }

        @Test
        void content_escapedHtml() {
            userMessageService.sendMessage(100L, MessageType.approval_result, "<script>alert(1)</script>", 1L);

            verify(userMessageMapper).insert(msgCaptor.capture());
            assertThat(msgCaptor.getValue().getContent())
                    .doesNotContain("<script>")
                    .contains("&lt;script&gt;")
                    .contains("alert(1)");
        }
    }

    // ========================================================================
    // markAsRead
    // ========================================================================

    @Nested
    class MarkAsRead {

        @Test
        void happyPath() {
            var msg = new UserMessage();
            msg.setMessageId(1L);
            msg.setUserId(100L);
            when(userMessageMapper.selectById(1L)).thenReturn(msg);

            userMessageService.markAsRead(1L, 100L);

            verify(userMessageMapper).markAsRead(1L);
        }

        @Test
        void nullMessageId_throws() {
            assertThatThrownBy(() -> userMessageService.markAsRead(null, 100L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullUserId_throws() {
            assertThatThrownBy(() -> userMessageService.markAsRead(1L, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void messageNotFound_throws() {
            when(userMessageMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> userMessageService.markAsRead(999L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不存在");
            verify(userMessageMapper, never()).markAsRead(any());
        }

        @Test
        void userIdMismatch_throws() {
            var msg = new UserMessage();
            msg.setMessageId(1L);
            msg.setUserId(999L); // 另一个用户
            when(userMessageMapper.selectById(1L)).thenReturn(msg);

            assertThatThrownBy(() -> userMessageService.markAsRead(1L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无权操作");
            verify(userMessageMapper, never()).markAsRead(any());
        }
    }

    // ========================================================================
    // listUserMessages
    // ========================================================================

    @Nested
    class ListUserMessages {

        @Test
        void happyPath() {
            var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserMessage>(1, 10);
            when(userMessageMapper.selectPage(any(), any())).thenReturn(page);

            var result = userMessageService.listUserMessages(100L, 1, 10);

            assertThat(result).isEmpty();
            verify(userMessageMapper).selectPage(any(), any());
        }

        @Test
        void emptyResult() {
            var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserMessage>(1, 10);
            when(userMessageMapper.selectPage(any(), any())).thenReturn(page);

            var result = userMessageService.listUserMessages(999L, 1, 10);

            assertThat(result).isEmpty();
            verify(userMessageMapper).selectPage(any(), any());
        }

        @Test
        void nullUserId_throws() {
            assertThatThrownBy(() -> userMessageService.listUserMessages(null, 1, 10))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ========================================================================
    // countUnread
    // ========================================================================

    @Nested
    class CountUnread {

        @Test
        void normalCount() {
            userMessageService.countUnread(100L);

            verify(userMessageMapper).countUnread(100L);
        }

        @Test
        void nullUserId_throws() {
            assertThatThrownBy(() -> userMessageService.countUnread(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
