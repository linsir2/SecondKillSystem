-- seckill_compensate.lua
-- 秒杀库存补偿 —— 原子 Lua 脚本
--
-- 在 MQ 发送失败或超时关单时执行逆向操作，恢复扣减前的 Redis 状态。
--
-- ⚠ ZREM 前置检查：只有移除成功才执行补偿，避免 SchedulerX 兜底与正常关单
-- 同时补偿导致 INCRBY 双倍加库存的竞赛态。
--
-- KEYS[1] = seckill:stock:{activityId}:{seckillGoodsId}      库存计数
-- KEYS[2] = seckill:users:{activityId}:{seckillGoodsId}      已购买用户集合
-- KEYS[3] = seckill:pending:{activityId}                     排队凭证 zset
-- ARGV[1] = userId
-- ARGV[2] = buyCount
-- ARGV[3] = orderToken
--
-- 返回:
--   1  补偿成功（当前调用执行了补偿操作）
--   0  无需补偿（ZREM 已由其他路径执行，或成员不存在）

local removed = redis.call('ZREM', KEYS[3], ARGV[3])
if removed == 0 then
    return 0
end

redis.call('INCRBY', KEYS[1], ARGV[2])
redis.call('SREM', KEYS[2], ARGV[1])
return 1
