package com.huyuans.cache.utils;

import org.springframework.core.NamedThreadLocal;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 缓存持有上下文
 *
 * @author huyuans
 * @date 2024/4/30
 */
public class CacheHolder {

    private static final ThreadLocal<Map<String, Duration>> CACHE_EXPIRE = new NamedThreadLocal<>("cache-holder");

    /**
     * 获取缓存的过期时间
     *
     * @param cacheName 缓存的cacheName
     * @return 过期时间
     */
    public static Duration getExpireTime(String cacheName) {
        Map<String, Duration> cache = CACHE_EXPIRE.get();
        if (cache == null) {
            return null;
        }
        return cache.get(cacheName);
    }

    /**
     * 设置缓存过期时间
     *
     * @param cacheName  缓存的cacheName
     * @param expireTime 过期时间
     */
    public static void setExpireTime(String cacheName, Duration expireTime) {
        Map<String, Duration> cache = CACHE_EXPIRE.get();
        if (cache == null) {
            cache = new HashMap<>();
        }
        cache.put(cacheName, expireTime);
        CACHE_EXPIRE.set(cache);
    }

    /**
     * 从当前线程中移除缓存
     *
     * @param cacheName 移除的cacheName
     */
    public static void remove(String cacheName) {
        Map<String, Duration> cache = CACHE_EXPIRE.get();
        if (cache == null) {
            return;
        }
        cache.remove(cacheName);
        if (cache.isEmpty()) {
            CACHE_EXPIRE.remove();
        }
    }
}
