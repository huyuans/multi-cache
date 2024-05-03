package com.huyuans.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.huyuans.cache.enums.CacheOperation;
import com.huyuans.cache.enums.CaffeineStrength;
import com.huyuans.cache.properties.CacheProperties;
import com.huyuans.cache.properties.CaffeineProperties;
import com.huyuans.cache.properties.RedisPropertiesExtend;
import com.huyuans.cache.utils.CacheHolder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Redis、Caffeine CacheManager
 *
 * @author huyuans
 * @date 2024/4/30
 */
@Slf4j
@Getter
@Setter
public class RedisCaffeineCacheManager implements CacheManager {

    private ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();

    private final CacheProperties cacheProperties;

    private final CaffeineProperties caffeineProperties;

    private final RedisPropertiesExtend redisPropertiesExtend;

    private final boolean dynamic;

    private final String serverId;

    private RedisTemplate<Object, Object> redisTemplate;

    private RedissonClient redissonClient;

    public RedisCaffeineCacheManager(CacheProperties cacheProperties, CaffeineProperties caffeineProperties,
                                     RedisPropertiesExtend redisPropertiesExtend) {
        this.cacheProperties = cacheProperties;
        this.caffeineProperties = caffeineProperties;
        this.redisPropertiesExtend = redisPropertiesExtend;
        this.dynamic = cacheProperties.isDynamic();
        this.serverId = redisPropertiesExtend.getServerId();
    }

    @Override
    public Cache getCache(String cacheName) {
        try {
            Cache cache = cacheMap.get(cacheName);
            if (cache != null) {
                return cache;
            }
            if (!dynamic) {
                return null;
            }
            return cacheMap.computeIfAbsent(cacheName, s -> {
                log.debug("create cache instance, the cache name is : {}", s);
                return createCache(s);
            });
        } finally {
            CacheHolder.remove(cacheName);
        }
    }

    public Cache createCache(String cacheName) {
        com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache = createCaffeineCache(cacheName);
        return new RedisCaffeineCache(cacheName, redisTemplate, redissonClient, caffeineCache, cacheProperties, redisPropertiesExtend);
    }

    public com.github.benmanes.caffeine.cache.Cache<Object, Object> createCaffeineCache(String cacheName) {
        Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder();
        Duration expireTime = CacheHolder.getExpireTime(cacheName);
        if (expireTime != null) {
            cacheBuilder.expireAfterAccess(expireTime);
            cacheBuilder.expireAfterWrite(expireTime);
        } else {
            doIfPresent(caffeineProperties.getExpireAfterAccess(), cacheBuilder::expireAfterAccess);
            doIfPresent(caffeineProperties.getExpireAfterWrite(), cacheBuilder::expireAfterWrite);
            doIfPresent(caffeineProperties.getRefreshAfterWrite(), cacheBuilder::refreshAfterWrite);
        }
        if (caffeineProperties.getInitialCapacity() > 0) {
            cacheBuilder.initialCapacity(caffeineProperties.getInitialCapacity());
        }
        if (caffeineProperties.getMaximumSize() > 0) {
            cacheBuilder.maximumSize(caffeineProperties.getMaximumSize());
        }
        if (caffeineProperties.getKeyStrength() != null) {
            if (CaffeineStrength.WEAK.equals(caffeineProperties.getKeyStrength())) {
                cacheBuilder.weakKeys();
            }
            if (CaffeineStrength.SOFT.equals(caffeineProperties.getKeyStrength())) {
                throw new UnsupportedOperationException("caffeine 不支持 key 软引用");
            }
        }
        if (caffeineProperties.getValueStrength() != null) {
            if (CaffeineStrength.WEAK.equals(caffeineProperties.getValueStrength())) {
                cacheBuilder.weakValues();
            }
            if (CaffeineStrength.SOFT.equals(caffeineProperties.getValueStrength())) {
                cacheBuilder.softValues();
            }
        }
        return cacheBuilder.build();
    }

    public void doIfPresent(Duration duration, Consumer<Duration> consumer) {
        if (duration != null && !duration.isNegative()) {
            consumer.accept(duration);
        }
    }

    @Override
    public Collection<String> getCacheNames() {
        return this.cacheMap.keySet();
    }

    public void clearLocal(String cacheName, Object key) {
        clearLocal(cacheName, key, CacheOperation.EVICT);
    }

    @SuppressWarnings("unchecked")
    public void clearLocal(String cacheName, Object key, CacheOperation operation) {
        Cache cache = cacheMap.get(cacheName);
        if (cache == null) {
            return;
        }
        RedisCaffeineCache redisCaffeineCache = (RedisCaffeineCache) cache;
        if (CacheOperation.EVICT_BATCH.equals(operation)) {
            redisCaffeineCache.clearLocalBatch((Iterable<Object>) key);
        } else {
            redisCaffeineCache.clearLocal(key);
        }
    }

}
