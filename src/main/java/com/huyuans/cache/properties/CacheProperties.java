package com.huyuans.cache.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 缓存全局配置
 *
 * @author huyuans
 * @date 2024/4/30
 */
@Data
@ConfigurationProperties(prefix = "spring.cache.multi")
public class CacheProperties {

    /**
     * 是否存储空值，默认true，防止缓存穿透
     */
    private boolean allowCacheNullValues = true;

    /**
     * 是否动态根据cacheName创建Cache的实现，默认true
     */
    private boolean dynamic = true;

}
