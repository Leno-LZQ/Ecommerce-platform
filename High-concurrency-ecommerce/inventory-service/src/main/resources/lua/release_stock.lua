-- KEYS[1]: stock:{skuId}
-- ARGV[1]: 回补数量

redis.call('INCRBY', KEYS[1], ARGV[1])
return 1
