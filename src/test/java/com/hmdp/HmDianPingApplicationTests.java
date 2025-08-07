package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    private final ShopServiceImpl shopService;

    @Autowired
    public HmDianPingApplicationTests(ShopServiceImpl shopService) {
        this.shopService = shopService;
    }

    @Test
    void testRedis() {
        System.out.println("测试redis");
        stringRedisTemplate.opsForValue().set("hmdp:test", "test");
    }

    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L, 10L);
    }
}