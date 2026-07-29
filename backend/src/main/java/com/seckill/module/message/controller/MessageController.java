package com.seckill.module.message.controller;

import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.Result;
import com.seckill.common.security.CurrentUser;
import com.seckill.common.security.SecurityContext;
import com.seckill.module.message.model.entity.UserMessage;
import com.seckill.module.message.model.vo.MessageVO;
import com.seckill.module.message.service.UserMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 消息中心 REST 接口 —— 消息列表、未读数、标记已读。
 */
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MessageController {

    private final UserMessageService userMessageService;

    /**
     * 分页查询当前用户消息。
     */
    @GetMapping
    public Result<List<MessageVO>> listMessages(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        CurrentUser user = SecurityContext.get();
        if (user == null) throw new BusinessException("未登录");

        int p = page != null ? page : 1;
        int s = size != null ? size : 20;
        List<UserMessage> entities = userMessageService.listUserMessages(user.getUserId(), p, s);
        List<MessageVO> vos = entities.stream()
                .map(e -> new MessageVO(
                        e.getMessageId(),
                        e.getMsgType().name(),
                        e.getContent(),
                        e.getActivityId(),
                        Boolean.TRUE.equals(e.getIsRead()),
                        e.getCreatedAt()))
                .toList();
        return Result.success(vos);
    }

    /**
     * 当前用户未读消息数。
     */
    @GetMapping("/unread/count")
    public Result<Long> countUnread() {
        CurrentUser user = SecurityContext.get();
        if (user == null) throw new BusinessException("未登录");
        return Result.success(userMessageService.countUnread(user.getUserId()));
    }

    /**
     * 标记消息为已读。
     */
    @PutMapping("/{messageId}/read")
    public Result<Void> markAsRead(@PathVariable Long messageId) {
        CurrentUser user = SecurityContext.get();
        if (user == null) throw new BusinessException("未登录");
        userMessageService.markAsRead(messageId, user.getUserId());
        return Result.success(null);
    }
}
