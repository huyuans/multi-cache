package com.huyuans.cache.config;

import cn.hutool.core.collection.ListUtil;
import com.huyuans.cache.enums.BooleanEnum;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * 排除掉RedisTemplate、Redisson自动装配
 *
 * @author huyuans
 * @date 2024/4/30
 */
public class RedisImportFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    public static final String REDIS_ENABLE_PROPERTIES = "spring.redis.enabled";

    private Boolean redisEnabled;

    private static final List<String> CLASSES = ListUtil.of("org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.redisson.spring.starter.RedissonAutoConfiguration");

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < matches.length; i++) {
            if (autoConfigurationClasses[i] != null && CLASSES.contains(autoConfigurationClasses[i])) {
                matches[i] = redisEnabled;
            } else {
                matches[i] = true;
            }
        }
        return matches;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.redisEnabled = Boolean.parseBoolean(environment.getProperty(REDIS_ENABLE_PROPERTIES, BooleanEnum.class, BooleanEnum.TRUE).name());
    }
}
