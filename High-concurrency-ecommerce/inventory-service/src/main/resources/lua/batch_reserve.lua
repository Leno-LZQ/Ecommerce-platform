-- 批量预扣：先检查所有 SKU 库存 → 全部足够才扣减 → 任一不足全部回滚
-- KEYS[n]: stock:{skuId1}, stock:{skuId2}, ...
-- ARGV[n]: qty1, qty2, ...

local n = #KEYS

-- 第一遍：检查
for i = 1, n do
    local stock = tonumber(redis.call('GET', KEYS[i]))
    local qty = tonumber(ARGV[i])
    if stock == nil or stock < qty then
        return {0, i, stock or 0, qty}  -- 失败: {0, 失败的SKU序号, 现有库存, 需求量}
    end
end

-- 第二遍：扣减
for i = 1, n do
    redis.call('DECRBY', KEYS[i], ARGV[i])
end

return {1}  -- 成功
