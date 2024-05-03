package com.huyuans.cache.custom;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 通过Redis Key生成serverId
 *
 * @author huyuans
 * @date 2024/4/30
 */
@RequiredArgsConstructor
public class RedisSequenceServerIdGenerator implements ServerIdGenerator {

    private final RedisTemplate<Object, Object> redisTemplate;

    private final String serverIdGeneratorKey;

    @Override
    public String get() {
        return String.valueOf(redisTemplate.opsForValue().increment(serverIdGeneratorKey));
    }

}
