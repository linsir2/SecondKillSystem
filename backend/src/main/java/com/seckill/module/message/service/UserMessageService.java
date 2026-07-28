package com.seckill.module.message.service;

import com.seckill.module.message.model.entity.UserMessage;
import com.seckill.module.message.model.enums.MessageType;

import java.util.List;

public interface UserMessageService {
    void sendMessage(Long userId, MessageType type, String content, Long activityId);
    List<UserMessage> listUserMessages(Long userId, int page, int size);
    void markAsRead(Long messageId);
    long countUnread(Long userId);
}
