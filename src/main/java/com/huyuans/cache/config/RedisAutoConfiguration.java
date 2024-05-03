package com.huyuans.cache.config;

import com.huyuans.cache.RedisCaffeineCacheManager;
import com.huyuans.cache.listener.CacheExpiredListener;
import com.huyuans.cache.listener.CacheMessageListener;
import com.huyuans.cache.properties.RedisPropertiesExtend;
import com.huyuans.cache.custom.RedisSequenceServerIdGenerator;
import com.huyuans.cache.custom.ServerIdGenerator;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis自动装配
 *
 * @author huyuans
 * @date 2024/4/30
 */
@Configuration
@AutoConfigureBefore(RedissonAutoConfiguration.class)
@ConditionalOnProperty(name = RedisImportFilter.REDIS_ENABLE_PROPERTIES, havingValue = "true", matchIfMissing = true)
public class RedisAutoConfiguration {

    /**
     * 是否键过期事件通知
     */
    private final boolean keyExpireNotify;

    public RedisAutoConfiguration(RedisPropertiesExtend redisPropertiesExtend) {
        this.keyExpireNotify = redisPropertiesExtend.isKeyExpireNotify();
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        if (keyExpireNotify) {
            // 开启键过期事件通知
            redisConnectionFactory.getConnection().serverCommands().setConfig("notify-keyspace-events", "Ex");
        }
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        return template;
    }

    @Bean
    @ConditionalOnMissingBean({StringRedisTemplate.class})
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }

    @Bean
    @SuppressWarnings("unchecked")
    @ConditionalOnMissingBean(name = "cacheMessageListener")
    public CacheMessageListener cacheMessageListener(RedisTemplate<Object, Object> redisTemplate,
                                                     RedisCaffeineCacheManager redisCaffeineCacheManager) {
        return new CacheMessageListener((RedisSerializer<Object>) redisTemplate.getValueSerializer(), redisCaffeineCacheManager);
    }

    @Bean
    @ConditionalOnMissingBean(name = "cacheExpiredListener")
    public CacheExpiredListener cacheExpiredListener(RedisCaffeineCacheManager redisCaffeineCacheManager) {
        return new CacheExpiredListener(redisCaffeineCacheManager);
    }

    @Bean
    @ConditionalOnMissingBean(name = "cacheMessageListenerContainer")
    public RedisMessageListenerContainer cacheMessageListenerContainer(RedisProperties redisProperties, RedisPropertiesExtend redisPropertiesExtend,
                                                                       RedisConnectionFactory redisConnectionFactory,
                                                                       CacheMessageListener cacheMessageListener,
                                                                       CacheExpiredListener cacheExpiredListener) {
        RedisMessageListenerContainer redisMessageListenerContainer = new RedisMessageListenerContainer();
        redisMessageListenerContainer.setConnectionFactory(redisConnectionFactory);
        redisMessageListenerContainer.addMessageListener(cacheMessageListener,
                new ChannelTopic(redisPropertiesExtend.getTopic()));
        if (keyExpireNotify) {
            // 监听键过期事件通知
            redisMessageListenerContainer.addMessageListener(cacheExpiredListener,
                    new PatternTopic(String.format("__keyevent@%d__:expired", redisProperties.getDatabase())));
        }
        return redisMessageListenerContainer;
    }

    @Bean
    @ConditionalOnMissingBean(ServerIdGenerator.class)
    public ServerIdGenerator redisSequenceServerIdGenerator(RedisTemplate<Object, Object> redisTemplate,
                                                            RedisPropertiesExtend redisPropertiesExtend) {
        return new RedisSequenceServerIdGenerator(redisTemplate, redisPropertiesExtend.getServerIdGeneratorKey());
    }
}
