package com.huyuans.cache.properties;

import com.huyuans.cache.enums.BooleanEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis配置扩展
 *
 * @author huyuans
 * @date 2024/4/30
 */
@Data
@ConfigurationProperties(prefix = "spring.redis")
public class RedisPropertiesExtend {

    /**
     * 是否开启
     */
    private BooleanEnum enabled = BooleanEnum.TRUE;

    /**
     * Cache默认的过期时间，默认不过期
     */
    private Duration defaultExpire = Duration.ZERO;

    /**
     * 空值的默认过期时间
     */
    private Duration defaultNullValueExipre = null;

    /**
     * 每个cacheName的过期时间，优先级比defaultExpiration高
     */
    private Map<String, Duration> expires = new HashMap<>();

    /**
     * 缓存更新时通知其他节点的topic名称
     */
    private String topic = "cache:redis:caffeine:topic";

    /**
     * 生成当前节点id的key，当配置了spring.redis.server-id时，该配置不生效
     */
    private String serverIdGeneratorKey = "cache:redis:caffeine:server-id-sequence";

    /**
     * 当前节点id。来自当前节点的缓存更新通知不会被处理
     */
    private String serverId;

    /**
     * 是否开启键过期时间通知（开启并监听redis的key过期事件，删除一级缓存）
     * 防止由二级缓存载入的一级缓存使用的是原本的过期时间导致：二级缓存已过期而一级缓存还存在）
     */
    private boolean keyExpireNotify = true;

}
