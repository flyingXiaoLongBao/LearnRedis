package com.hmdp;

import com.hmdp.entity.Voucher;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;


    /*
    * 开启一个线程池
    * */
    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testRedis() {
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + 10, "49");
    }

    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    void testIdWorker() throws InterruptedException {
        LocalDateTime beginTime = LocalDateTime.now();

        Runnable task = () -> {
            for (int i = 0; i < 1000; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
        };

        for(int i = 0; i < 300; i++){
            es.submit(task);
        }

        // 等待所有任务完成
        es.shutdown();
        while (!es.isTerminated()) {
            Thread.sleep(100);
        }

        LocalDateTime endTime = LocalDateTime.now();
        System.out.println("耗时：" + (endTime.getNano() - beginTime.getNano()));
    }

    @Test
    void testAddSeckillVoucher(){
        Voucher voucher = new Voucher();

    }

    @Test
    void testRedisson() throws InterruptedException {
        //创建一把锁（可重入），指定锁的名称
        RLock lock = redissonClient.getLock("hmdp:lock:test");
        //尝试获取锁 参数分别是:获取锁的最大等待时间（期间会重试），锁自动释放时间，时间单位
        boolean isLock = lock.tryLock(1,10, TimeUnit.SECONDS);

        //判断是否成功
        if(isLock){
            try {
                System.out.println("执行业务逻辑");
            } finally {
                //释放锁
                lock.unlock();
            }
        }
    }
}