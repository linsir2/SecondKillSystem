-- seckill_compensate.lua
-- 秒杀库存补偿 —— 原子 Lua 脚本
--
-- 在 MQ 发送失败时执行逆向操作，恢复扣减前的 Redis 状态。
--
-- KEYS[1] = seckill:stock:{activityId}:{seckillGoodsId}      库存计数
-- KEYS[2] = seckill:users:{activityId}:{seckillGoodsId}      已购买用户集合
-- KEYS[3] = seckill:pending:{activityId}                     排队凭证 zset
-- ARGV[1] = userId
-- ARGV[2] = buyCount
-- ARGV[3] = orderToken

redis.call('INCRBY', KEYS[1], ARGV[2])
redis.call('SREM', KEYS[2], ARGV[1])
redis.call('ZREM', KEYS[3], ARGV[3])
return 1
