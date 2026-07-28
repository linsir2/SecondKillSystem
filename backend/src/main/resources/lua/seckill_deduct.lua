-- seckill_deduct.lua
-- 秒杀库存预扣 —— 原子 Lua 脚本
--
-- KEYS[1] = seckill:stock:{activityId}:{seckillGoodsId}      库存计数
-- KEYS[2] = seckill:users:{activityId}:{seckillGoodsId}      已购买用户集合
-- KEYS[3] = seckill:limit:{activityId}:{seckillGoodsId}      单次限购数（预热时写入）
-- ARGV[1] = userId
-- ARGV[2] = buyCount
--
-- 返回:
--   1  预扣成功
--  -1  重复购买（用户已在集合）
--  -2  库存不足（stock < buyCount 或 key 不存在）
--  -3  单次超限购（buyCount > limitNum）

local stockKey = KEYS[1]
local usersKey = KEYS[2]
local limitKey = KEYS[3]
local userId = ARGV[1]
local buyCount = tonumber(ARGV[2])

-- 防护：非法 buyCount
if not buyCount or buyCount <= 0 then
    return -2
end

-- 检查 A: 重复购买
if redis.call('SISMEMBER', usersKey, userId) == 1 then
    return -1
end

-- 检查 B: 单次限购（limit key 不存在时跳过检查）
local limitNum = tonumber(redis.call('GET', limitKey))
if limitNum and buyCount > limitNum then
    return -3
end

-- 检查 C: 库存（key 不存在或非数字视为 0）
local stock = tonumber(redis.call('GET', stockKey))
if not stock or stock < buyCount then
    return -2
end

-- 执行: 原子扣减
redis.call('DECRBY', stockKey, buyCount)
redis.call('SADD', usersKey, userId)
return 1
