package com.hmdp.utils;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    /*
    * 开始时间戳
    * */
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    /*
    * 序列号位数
    * */
    private static final int COUNT_BITS = 32;


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //生成序列号
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd")));

        //拼接返回
        return timestamp << COUNT_BITS | count;
    }
}
