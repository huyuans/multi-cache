package com.huyuans.cache.custom;

import com.huyuans.cache.properties.RedisPropertiesExtend;

import java.util.function.Supplier;

/**
 * 当前服务id生成器 见{@link RedisPropertiesExtend#serverId}，当且仅当二级缓存（spring.redis.enabled=true）生效
 *
 * @author huyuans
 * @date 2024/4/30
 */
public interface ServerIdGenerator extends Supplier<String> {

}
