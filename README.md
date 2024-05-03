# Multi-Cache

Multi-Cache 支持 Caffeine 和 Redis 二级缓存，提供在单机和集群环境下的并发安全读写，并通过 Redis 的发布与订阅和键事件通知机制来保证数据一致性。此外，它允许用户根据需要配置缓存项的过期时间、缓存穿透等。

## 特性

- 支持 Caffeine 和 Redis 作为一级和二级缓存。
- 支持在单机和集群环境中使用，并发安全读写。
- 基于 Redis 的发布与订阅和键事件通知机制保证数据一致性。
- 可配置化的缓存项过期时间。
- 支持缓存穿透。
- 支持编程式开发。
- 支持原生注解@Cacheable、@CacheEvict、@CachePut、@Caching注解开发。



## 编程式Demo

```java
@Component
public class Example {

    @Autowired
    private CacheManager cacheManager;

    private final String cacheName = "stu";

    /**
     * 写入一、二级缓存
     */
    public void put() {
        Cache cache = getOrCreateCache(cacheName);
        cache.put("name", "huyuans");
    }

    /**
     * 获取缓存
     */
    public String get() {
        Cache stuCache = getOrCreateCache(cacheName);
        Cache.ValueWrapper valueWrapper = stuCache.get("name");
        return valueWrapper == null ? "找不到" : "结果为：" + valueWrapper.get();
    }

    private Cache getOrCreateCache(String cacheName) {
        // 设置过期时间为60s，不设置则以默认过期时间创建cache
        CacheHolder.setExpireTime(cacheName, Duration.ofSeconds(60L));
        // 创建并缓存该cache到本地
        return cacheManager.getCache(cacheName);
    }

}
  
```