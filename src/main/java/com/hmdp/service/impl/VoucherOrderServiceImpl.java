package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private IVoucherOrderService proxy;

    //创建阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    //开启线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 预加载秒杀脚本
    private static DefaultRedisScript<Long> SECKILL_SCRIPT;

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    //获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常",  e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //获取用户id
        Long userId = voucherOrder.getUserId();

        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        SimpleRedisLock redisLock = new SimpleRedisLock("order:userId:" + userId, stringRedisTemplate);
        //获取锁
//        boolean isLock = redisLock.tryLock(10L);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            //4.2获取锁失败，返回
            log.error("不允许重复下单");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        }finally {
//            redisLock.unlock();
            lock.unlock();
        }

    }

    // 在类初始化时加载Lua脚本；开启VoucherOrderHandler线程
    @PostConstruct
    public void init() {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setScriptText(loadLuaScriptText("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /**
     * 加载Lua脚本内容
     * @param scriptName 脚本名称
     * @return 脚本内容
     */
    private String loadLuaScriptText(String scriptName) {
        try (InputStream inputStream = new ClassPathResource(scriptName).getInputStream();
             Scanner scanner = new Scanner(inputStream)) {
            return scanner.useDelimiter("\\A").next();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Lua script: " + scriptName, e);
        }
    }

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
        //3.执行Lua脚本，完成库存判断、扣减和用户下单判断
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );

        int resultValue = result.intValue();
        //4.根据Lua脚本返回结果判断执行情况
        if (resultValue == 1) {
            //库存不足
            return Result.fail("库存不足");
        } else if (resultValue == 2) {
            //用户已下单
            return Result.fail("用户已经下过单");
        }

        //5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //5.1订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //5.2用户id
        voucherOrder.setUserId(userId);
        //5.3代金券id
        voucherOrder.setVoucherId(voucherId);
        //6.将订单信息保存到阻塞队列中
        orderTasks.add(voucherOrder);
        //获取代理对象(事务)
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //7.返回订单id
        return Result.ok(orderId);
    }


    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //在数据库中扣减库存
        Long voucherId = voucherOrder.getVoucherId();
        seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        //保存订单信息到数据库
        this.save(voucherOrder);
    }
    /*    @Override
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
        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        SimpleRedisLock redisLock = new SimpleRedisLock("order:userId:" + userId, stringRedisTemplate);
        //获取锁
//        boolean isLock = redisLock.tryLock(10L);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            //4.2获取锁失败，返回异常
            return Result.fail("不允许重复下单");
        }
        try {
            //获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
//            redisLock.unlock();
            lock.unlock();
        }
    }*/
}
