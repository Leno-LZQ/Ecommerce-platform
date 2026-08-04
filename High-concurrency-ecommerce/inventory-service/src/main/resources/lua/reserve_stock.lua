-- KEYS[1]: stock:{skuId}
-- ARGV[1]: 扣减数量
-- 返回值: 1=成功, 0=库存不足, -1=key不存在

local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then
    return -1
end
if stock >= tonumber(ARGV[1]) then
    redis.call('DECRBY', KEYS[1], ARGV[1])
    return 1
else
    return 0
end
