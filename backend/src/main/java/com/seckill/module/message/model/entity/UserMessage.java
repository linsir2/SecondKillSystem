package com.seckill.module.message.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.seckill.module.message.model.enums.MessageType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_message")
public class UserMessage {
    @TableId(type = IdType.ASSIGN_ID)
    private Long messageId;
    private Long userId;
    private MessageType msgType;
    private String content;
    private Long activityId;
    @TableField("is_read")
    private Boolean isRead;
    private LocalDateTime createdAt;
}
