-- Lua脚本：处理优惠券下单流程
-- 接收两个参数：优惠券ID(voucherId)和用户ID(userId)

local function processVoucherOrder(voucherId, userId)
    -- 检查参数
    if not voucherId or not userId then
        --print("参数错误：优惠券ID或用户ID不能为空")
        return -1
    end

    --print("开始处理优惠券订单")
    --print("优惠券ID: " .. voucherId)
    --print("用户ID: " .. userId)

    -- 1. 判断库存是否充足
    -- 这里模拟从Redis或数据库获取库存信息
    local stockKey = "seckill:stock:" .. voucherId
    local currentStock = redis.call('GET', stockKey) or 0
    currentStock = tonumber(currentStock)

    --print("当前库存: " .. currentStock)

    if currentStock <= 0 then
        --print("库存不足，返回 1")
        return 1
    end

    -- 2. 判断用户是否已经下单
    -- 检查用户ID是否已经在该优惠券订单的用户集合中
    local userSetKey = "voucherOrder:users:" .. voucherId
    local userExists = redis.call('SISMEMBER', userSetKey, userId)

    --print("用户是否已下单: " .. (userExists == 1 and "是" or "否"))

    if userExists == 1 then
        --print("用户已下单，返回 2")
        return 2
    else
        -- 3. 扣减库存
        redis.call('DECR', stockKey)
        --print("扣减库存成功")

        -- 4. 将userId存入当前优惠券的set集合
        redis.call('SADD', userSetKey, userId)
        --print("用户ID已添加到优惠券用户集合")

        --print("处理成功，返回 0")
        return 0
    end
end

-- 获取传入的参数
local voucherId = ARGV[1]
local userId = ARGV[2]

-- 调用主函数
return processVoucherOrder(voucherId, userId)
