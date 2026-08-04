-- ============================================================
-- 下单优惠锁定（原子执行，任一失败全部回滚）
-- ============================================================
-- KEYS 布局:
--   [1..N]:           coupon:lock:{couponCode}     × N 张券
--   [N+1..N+B]:       promo:budget:{activityId}     × B 个活动（仅含有限预算的活动）
--   [N+B+1..N+B+D]:   promo:daily:{activityId}:{userId}:{yyyyMMdd} × D （仅含每日限制的活动）
--
-- ARGV 布局:
--   [1] = N          券数量
--   [2] = B          预算 key 数量
--   [3] = D          每日限制 key 数量
--   [4] = lock_ttl   券锁 TTL（秒），例如 900
--   [5] = daily_ttl  每日计数 TTL（秒，到当日午夜），Java 端计算后传入
--   [6 .. 6+B-1]     预算扣减额（分），按 budget key 顺序一一对应
--   [6+B .. 6+B+D-1] 每日参与上限，按 daily key 顺序一一对应
--
-- 返回值:
--   1   全部成功（券锁已 SET + 预算已扣 + 每日计数已增）
--  -1   至少一张券已被占用
--  -2   至少一个活动预算不足
--  -3   至少一个活动已达每日参与上限
-- ============================================================

local N = tonumber(ARGV[1])   -- 券数
local B = tonumber(ARGV[2])   -- 预算 key 数
local D = tonumber(ARGV[3])   -- 每日限制 key 数
local lock_ttl = ARGV[4]      -- 券锁 TTL 秒
local daily_ttl = ARGV[5]     -- 每日计数 TTL 秒

-- ================= 第 1 遍：全量校验 =================

-- ① 校验所有券锁均不存在
for i = 1, N do
    if redis.call('EXISTS', KEYS[i]) == 1 then
        return -1
    end
end

-- ② 校验所有预算充足
local budget_start = 6
for i = 1, B do
    local budget_key = KEYS[N + i]
    local current = tonumber(redis.call('GET', budget_key)) or 0
    local deduct = tonumber(ARGV[budget_start + i - 1])
    if current < deduct then
        return -2
    end
end

-- ③ 校验每日限制（先 INCR 后判断，超限 DECR 回滚）
local daily_start = budget_start + B
for i = 1, D do
    local daily_key = KEYS[N + B + i]
    local limit = tonumber(ARGV[daily_start + i - 1])
    local count = redis.call('INCR', daily_key)
    -- 首次计数设置 TTL
    if count == 1 then
        redis.call('EXPIRE', daily_key, daily_ttl)
    end
    if count > limit then
        redis.call('DECR', daily_key)   -- 回滚已加的计数
        return -3
    end
end

-- ================= 第 2 遍：全部执行写入 =================

-- ④ 设置所有券的锁标记
for i = 1, N do
    redis.call('SET', KEYS[i], '1', 'EX', lock_ttl)
end

-- ⑤ 扣减所有活动预算（第 2 遍校验已在步骤②完成，不会再失败）
for i = 1, B do
    local budget_key = KEYS[N + i]
    local deduct = ARGV[budget_start + i - 1]
    redis.call('DECRBY', budget_key, deduct)
end

-- 每日计数已在步骤③中 INCR，无需再操作

return 1
