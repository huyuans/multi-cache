package com.huyuans.cache.listener;

import com.huyuans.cache.RedisCaffeineCacheManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.Objects;

/**
 * 基于Redis发布订阅
 *
 * @author huyuans
 * @date 2024/4/30
 */
@Slf4j
@Getter
@RequiredArgsConstructor
public class CacheMessageListener implements MessageListener {

    private final RedisSerializer<Object> redisSerializer;

    private final RedisCaffeineCacheManager redisCaffeineCacheManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        CacheMessage cacheMessage = (CacheMessage) redisSerializer.deserialize(message.getBody());
        if (!Objects.equals(cacheMessage.getServerId(), redisCaffeineCacheManager.getServerId())) {
            log.debug("receive a redis topic message, clear local cache, the cacheName is {}, operation is {}, the key is {}",
                    cacheMessage.getCacheName(), cacheMessage.getOperation(), cacheMessage.getKey());
            redisCaffeineCacheManager.clearLocal(cacheMessage.getCacheName(), cacheMessage.getKey(), cacheMessage.getOperation());
        }
    }

}
