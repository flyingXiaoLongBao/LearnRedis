package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Test
    void testRedis() {
        System.out.println("测试redis");
        stringRedisTemplate.opsForValue().set("hmdp:test","test");
    }

}
