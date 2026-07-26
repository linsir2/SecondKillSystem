-- seckill_deduct.lua
-- 秒杀库存预扣 —— 原子 Lua 脚本
--
-- KEYS[1] = seckill:stock:{activityId}:{seckillGoodsId}   库存计数
-- KEYS[2] = seckill:users:{activityId}:{seckillGoodsId}   已购买用户集合
-- ARGV[1] = userId
--
-- 返回:
--   1  预扣成功
--  -1  重复购买（用户已在集合）
--  -2  库存不足（stock <= 0 或 key 不存在）

local stockKey = KEYS[1]
local usersKey = KEYS[2]
local userId = ARGV[1]

-- 检查 A: 重复购买
if redis.call('SISMEMBER', usersKey, userId) == 1 then
    return -1
end

-- 检查 B: 库存（key 不存在或非数字视为 0）
local stock = tonumber(redis.call('GET', stockKey))
if not stock or stock <= 0 then
    return -2
end

-- 执行 C: 原子扣减
redis.call('DECRBY', stockKey, 1)
redis.call('SADD', usersKey, userId)
return 1
