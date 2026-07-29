package com.seckill.module.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seckill.common.exception.BusinessException;
import com.seckill.module.message.mapper.UserMessageMapper;
import com.seckill.module.message.model.entity.UserMessage;
import com.seckill.module.message.model.enums.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserMessageServiceImpl implements UserMessageService {

    private final UserMessageMapper userMessageMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendMessage(Long userId, MessageType type, String content, Long activityId) {
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        if (type == null) throw new IllegalArgumentException("msgType must not be null");
        if (content == null) throw new IllegalArgumentException("content must not be null");

        UserMessage msg = new UserMessage();
        msg.setUserId(userId);
        msg.setMsgType(type);
        msg.setContent(HtmlUtils.htmlEscape(content));
        msg.setActivityId(activityId);
        msg.setIsRead(false);
        msg.setCreatedAt(LocalDateTime.now());

        userMessageMapper.insert(msg);
    }

    @Override
    public List<UserMessage> listUserMessages(Long userId, int page, int size) {
        if (userId == null) throw new IllegalArgumentException("userId must not be null");

        Page<UserMessage> p = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<UserMessage>()
                .eq(UserMessage::getUserId, userId)
                .orderByDesc(UserMessage::getCreatedAt);
        return userMessageMapper.selectPage(p, wrapper).getRecords();
    }

    @Override
    public void markAsRead(Long messageId, Long userId) {
        if (messageId == null) throw new IllegalArgumentException("messageId must not be null");
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        UserMessage msg = userMessageMapper.selectById(messageId);
        if (msg == null) throw new BusinessException("消息不存在");
        if (!msg.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该消息");
        }
        userMessageMapper.markAsRead(messageId);
    }

    @Override
    public long countUnread(Long userId) {
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        return userMessageMapper.countUnread(userId);
    }
}
