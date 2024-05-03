package com.huyuans.cache.custom;

import com.huyuans.cache.RedisCaffeineCacheManager;

/**
 * 修改 {@link RedisCaffeineCacheManager} 的回调
 *
 * @author huyuans
 * @date 2024/4/30
 */
@FunctionalInterface
public interface RedisCaffeineCacheManagerCustomizer {

    /**
     * 修改 {@link RedisCaffeineCacheManager}
     *
     * @param cacheManager cacheManager
     */
    void customize(RedisCaffeineCacheManager cacheManager);

}
