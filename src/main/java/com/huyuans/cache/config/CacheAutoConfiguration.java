package com.huyuans.cache.config;

import cn.hutool.core.util.StrUtil;
import com.huyuans.cache.RedisCaffeineCacheManager;
import com.huyuans.cache.custom.RedisCaffeineCacheManagerCustomizer;
import com.huyuans.cache.aspect.CacheExpireAspect;
import com.huyuans.cache.custom.ServerIdGenerator;
import com.huyuans.cache.properties.CacheProperties;
import com.huyuans.cache.properties.CaffeineProperties;
import com.huyuans.cache.properties.RedisPropertiesExtend;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 二级缓存自动装配
 *
 * @author huyuans
 * @date 2024/4/30
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableConfigurationProperties({CacheProperties.class, CaffeineProperties.class, RedisPropertiesExtend.class})
public class CacheAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public RedisCaffeineCacheManager cacheManager(CacheProperties cacheProperties,
                                                  CaffeineProperties caffeineProperties,
                                                  RedisPropertiesExtend redisPropertiesExtend,
                                                  ObjectProvider<RedisTemplate<Object, Object>> redisTemplate,
                                                  ObjectProvider<RedissonClient> redissonClient,
                                                  ObjectProvider<ServerIdGenerator> serverIdGenerator,
                                                  ObjectProvider<RedisCaffeineCacheManagerCustomizer> cacheManagerCustomizer) {
        if (StrUtil.isBlank(redisPropertiesExtend.getServerId())) {
            serverIdGenerator.ifAvailable(idGenerator -> redisPropertiesExtend.setServerId(idGenerator.get()));
        }
        RedisCaffeineCacheManager cacheManager = new RedisCaffeineCacheManager(cacheProperties, caffeineProperties, redisPropertiesExtend);
        redisTemplate.ifAvailable(cacheManager::setRedisTemplate);
        redissonClient.ifAvailable(cacheManager::setRedissonClient);
        cacheManagerCustomizer.ifAvailable(customizer -> customizer.customize(cacheManager));
        return cacheManager;
    }

    @Bean
    public CacheExpireAspect expireAspect() {
        return new CacheExpireAspect();
    }
}
