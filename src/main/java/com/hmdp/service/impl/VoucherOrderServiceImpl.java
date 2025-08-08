package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //2.判断活动是否开始
        LocalDateTime beginTime = voucher.getBeginTime();
        LocalDateTime endTime = voucher.getEndTime();
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(beginTime) || now.isAfter(endTime)) {
            //2.1不在活动期限内，返回异常
            return Result.fail("不在秒杀活动的活动期间内");
        }
        //2.2在活动期间
        //3.判断库存是否充足
        if (voucher.getStock() < 1) {
            //3.1库存不充足，返回异常
            return Result.fail("库存不足");
        }
        //3.2库存充足
        //4.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            //4.1扣减库存失败，返回异常
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //创建锁对象
        SimpleRedisLock redisLock = new SimpleRedisLock("order:userId:" + userId, stringRedisTemplate);
        //获取锁
        boolean isLock = redisLock.tryLock(10L);
        if (!isLock) {
            //4.2获取锁失败，返回异常
            return Result.fail("不允许重复下单");
        }
        try {
            //获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
            redisLock.unlock();
        }
    }

    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //一人一单
        //根据秒杀券的id和用户id判断用户是否已经下过单
        Long userId = UserHolder.getUser().getId();


        Integer count = query().eq("voucher_id", voucherId)
                .eq("user_id", userId)
                .count();

        if (count > 0) {
            //5.1已经下过单，返回异常
            return Result.fail("用户已经下过单");
        }

        //5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //5.1订单id
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //5.2用户id

        voucherOrder.setUserId(userId);
        //5.3代金券id
        voucherOrder.setVoucherId(voucherId);
        //6.保存订单
        this.save(voucherOrder);
        //7.返回订单id
        return Result.ok(orderId);
    }
}
