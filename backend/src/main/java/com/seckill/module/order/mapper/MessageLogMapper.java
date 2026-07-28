package com.seckill.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.module.order.model.entity.MessageLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 本地消息表 Mapper。
 */
@Mapper
public interface MessageLogMapper extends BaseMapper<MessageLog> {
}
