package com.seckill.module.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.module.message.model.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {

    @Update("UPDATE user_message SET is_read = 1 WHERE message_id = #{messageId}")
    void markAsRead(@Param("messageId") Long messageId);

    @Select("SELECT COUNT(*) FROM user_message WHERE user_id = #{userId} AND is_read = 0")
    long countUnread(@Param("userId") Long userId);
}
