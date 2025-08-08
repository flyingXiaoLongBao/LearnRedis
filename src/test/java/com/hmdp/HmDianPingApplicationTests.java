package com.hmdp;

import com.hmdp.entity.Voucher;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;


    /*
    * 开启一个线程池
    * */
    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testRedis() {
        System.out.println("测试redis");
        stringRedisTemplate.opsForValue().set("hmdp:test", "test");
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
}