package com.huyuans.cache;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.huyuans.cache.enums.CacheOperation;
import com.huyuans.cache.listener.CacheMessage;
import com.huyuans.cache.properties.CacheProperties;
import com.huyuans.cache.properties.RedisPropertiesExtend;
import com.huyuans.cache.utils.CacheHolder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.NullValue;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Redis、Caffeine二级缓存，支持集群环境
 *
 * @author huyuans
 * @date 2024/4/30
 */
@Slf4j
@Getter
@Setter
@SuppressWarnings("unchecked")
public class RedisCaffeineCache extends AbstractValueAdaptingCache implements Cache<Object, Object> {

    private final String cacheName;

    private final Cache<Object, Object> caffeineCache;

    private final RedisTemplate<Object, Object> redisTemplate;

    private final RedissonClient redissonClient;

    private final String getRedisKeyPrefix;

    /**
     * redis空值的过期时间
     */
    private final Duration redisNullValueExpire;

    /**
     * redis非空值的过期时间
     */
    private final Duration redisExpire;

    private final String topic;

    private final String serverId;

    private final boolean redisEnabled;

    private final Map<String, ReentrantLock> keyLockMap = new ConcurrentHashMap<>();

    private final Map<String, ReentrantLock> caffeineWriteLockMap = new ConcurrentHashMap<>();

    public RedisCaffeineCache(String cacheName, RedisTemplate<Object, Object> redisTemplate,
                              RedissonClient redissonClient, Cache<Object, Object> caffeineCache, CacheProperties cacheProperties,
                              RedisPropertiesExtend redisPropertiesExtend) {
        super(cacheProperties.isAllowCacheNullValues());
        this.cacheName = cacheName;
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.caffeineCache = caffeineCache;
        this.getRedisKeyPrefix = StrUtil.removeSuffix(cacheName, ":") + ":";
        Duration expireTime = CacheHolder.getExpireTime(cacheName);
        if (expireTime != null) {
            this.redisExpire = expireTime;
        } else {
            this.redisExpire = ObjectUtil.defaultIfNull(redisPropertiesExtend.getExpires().get(cacheName), redisPropertiesExtend.getDefaultExpire());
        }
        this.redisNullValueExpire = ObjectUtil.defaultIfNull(redisPropertiesExtend.getDefaultNullValueExipre(), this.redisExpire);
        this.topic = redisPropertiesExtend.getTopic();
        this.serverId = redisPropertiesExtend.getServerId();
        this.redisEnabled = Boolean.parseBoolean(redisPropertiesExtend.getEnabled().name());
    }

    @Override
    public String getName() {
        return this.cacheName;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object value = lookup(key);
        if (value != null) {
            return (T) value;
        }
        try {
            value = valueLoader.call();
            // 不允许空值且value为空，则抛出异常
            value = toStoreValue(value);
        } catch (IllegalArgumentException e) {
            throw new ValueRetrievalException(key, valueLoader, e.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 一、二级缓存
        if (redisEnabled) {
            // 分布式锁，集群环境下保证写入唯一
            boolean success = setRedisValueConcurrency(key, value, getRedisExipre(value), redisTemplate.opsForValue());
            if (success) {
                push(key, CacheOperation.EVICT);
                setCaffeineValue(key, value);
                return (T) value;
            } else {
                return (T) lookup(key);
            }
        }
        // 仅一级缓存
        boolean success = setCaffeineValueConcurrency(key, value);
        return success ? (T) value : (T) getCaffeineValue(key);
    }

    @Override
    public void put(Object key, Object value) {
        // 不允许空值且value为空，则抛出异常
        value = toStoreValue(value);
        // 一、二级缓存
        if (redisEnabled) {
            // 分布式锁，保证写入二级缓存、一级缓存为原子操作
            String keyName = "lock" + ":" + getRedisKeyPrefix + key;
            RLock rLock = redissonClient.getLock(keyName);
            try {
                rLock.lock();
                setRedisValue(key, value, getRedisExipre(value), redisTemplate.opsForValue());
                clearLocal(key);
                push(key, CacheOperation.EVICT);
                setCaffeineValue(key, value);
            } finally {
                rLock.unlock();
            }
            return;
        }
        setCaffeineValue(key, value);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        // 不允许空值且value为空，则抛出异常
        value = toStoreValue(value);
        Object prevValue = this.lookup(key);
        // 存在，返回
        if (prevValue != null) {
            return toValueWrapper(prevValue);
        }
        // 一、二级缓存
        if (redisEnabled) {
            // 分布式锁，集群环境下保证写入唯一
            boolean success = setRedisValueConcurrency(key, value, getRedisExipre(value), redisTemplate.opsForValue());
            if (success) {
                push(key, CacheOperation.EVICT);
                setCaffeineValue(key, value);
                return null;
            } else {
                return toValueWrapper(lookup(key));
            }
        }
        // 仅一级缓存
        boolean success = setCaffeineValueConcurrency(key, value);
        // 存在，返回旧值
        return success ? null : toValueWrapper(getCaffeineValue(key));
    }

    @Override
    public void evict(Object key) {
        if (redisEnabled) {
            // 先清除redis中缓存数据，然后清除caffeine中的缓存，避免短时间内如果先清除caffeine缓存后其他请求会再从redis里加载到caffeine中
            redisTemplate.delete(getRedisKey(key));
            push(key, CacheOperation.EVICT);
        }
        caffeineCache.invalidate(key.toString());
    }

    @Override
    public void clear() {
        if (redisEnabled) {
            // 先清除redis中缓存数据，然后清除caffeine中的缓存，避免短时间内如果先清除caffeine缓存后其他请求会再从redis里加载到caffeine中
            Set<Object> keys = redisTemplate.keys(this.cacheName.concat(":*"));
            if (CollUtil.isNotEmpty(keys)) {
                redisTemplate.delete(keys);
            }
            push(null, CacheOperation.EVICT);
        }
        caffeineCache.invalidateAll();
    }

    @Override
    protected Object lookup(Object key) {
        // 从咖啡因获取
        Object value = getCaffeineValue(key);
        if (value != null) {
            log.debug("get cache from caffeine, the key is : {}", getRedisKey(key));
            return value;
        }
        // 从Redis获取
        if (redisEnabled) {
            value = getRedisValue(key);
            if (value != null) {
                log.debug("get cache from redis and put in caffeine, the key is : {}", getRedisKey(key));
                boolean success = setCaffeineValueConcurrency(key, value);
                return success ? value : getCaffeineValue(key);
            }
        }
        return value;
    }

    /**
     * 缓存变更时通知其他节点清理本地缓存
     */
    private void push(Object key, CacheOperation operation) {
        CacheMessage cacheMessage = new CacheMessage(this.serverId, this.cacheName, operation, key);
        redisTemplate.convertAndSend(topic, cacheMessage);
    }

    /**
     * 清理本地缓存
     */
    public void clearLocal(Object key) {
        log.debug("clear local cache, the key is : {}", key);
        if (key == null) {
            caffeineCache.invalidateAll();
        } else {
            caffeineCache.invalidate(key.toString());
        }
    }

    public void clearLocalBatch(Iterable<Object> keys) {
        log.debug("clear local cache, the keys is : {}", keys);
        caffeineCache.invalidateAll(keys);
    }

    protected void setRedisValue(Object key, Object value, Duration expire, ValueOperations<Object, Object> valueOperations) {
        if (!expire.isNegative() && !expire.isZero()) {
            valueOperations.set(getRedisKey(key), value, expire);
        } else {
            valueOperations.set(getRedisKey(key), value);
        }
    }

    protected boolean setRedisValueConcurrency(Object key, Object value, Duration expire, ValueOperations<Object, Object> valueOperations) {
        if (!expire.isNegative() && !expire.isZero()) {
            return Boolean.TRUE.equals(valueOperations.setIfAbsent(getRedisKey(key), value, expire));
        }
        return Boolean.TRUE.equals(valueOperations.setIfAbsent(getRedisKey(key), value));
    }

    protected Object getRedisValue(Object key) {
        return redisTemplate.opsForValue().get(getRedisKey(key));
    }

    protected Object getRedisKey(Object key) {
        return this.getRedisKeyPrefix + key;
    }

    protected Duration getRedisExipre(Object value) {
        if (value == null || value == NullValue.INSTANCE) {
            return redisNullValueExpire;
        }
        return redisExpire;
    }

    protected void setCaffeineValue(Object key, Object value) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(value);
            caffeineCache.put(key.toString(), byteArrayOutputStream.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected boolean setCaffeineValueConcurrency(Object key, Object value) {
        ReentrantLock lock = caffeineWriteLockMap.computeIfAbsent(key.toString(), s -> {
            log.trace("create caffeine write lock for key : {}", s);
            return new ReentrantLock();
        });
        try {
            lock.lock();
            Object preValue = getCaffeineValue(key);
            if (preValue != null) {
                return false;
            }
            setCaffeineValue(key, value);
            return true;
        } finally {
            lock.unlock();
        }
    }

    protected Object getCaffeineValue(Object key) {
        Object value = caffeineCache.getIfPresent(key.toString());
        if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
                 ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
                return objectInputStream.readObject();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return value;
    }

    // ---------- 对 Caffeine Cache 接口的实现

    @Override
    public @Nullable Object getIfPresent(@NonNull Object key) {
        ValueWrapper valueWrapper = get(key);
        if (valueWrapper == null) {
            return null;
        }
        return valueWrapper.get();
    }

    @Override
    public @Nullable Object get(@NonNull Object key, @NonNull Function<? super Object, ?> mappingFunction) {
        return get(key, (Callable<Object>) () -> mappingFunction.apply(key));
    }

    @Override
    public @NonNull Map<@NonNull Object, @NonNull Object> getAllPresent(@NonNull Iterable<@NonNull ?> keys) {
        GetAllContext context = new GetAllContext((Iterable<Object>) keys);
        doGetAll(context);
        Map<Object, Object> cachedKeyValues = context.cachedKeyValues;
        Map<Object, Object> result = new HashMap<>(cachedKeyValues.size(), 1);
        cachedKeyValues.forEach((k, v) -> result.put(k, fromStoreValue(v)));
        return result;
    }

    @Override
    public @NonNull Map<Object, Object> getAll(@NonNull Iterable<?> keys, @NonNull Function<Iterable<?>, @NonNull Map<Object, Object>> mappingFunction) {
        GetAllContext context = new GetAllContext((Iterable<Object>) keys);
        context.saveRedisAbsentKeys = true;
        doGetAll(context);
        int redisAbsentCount = context.redisAbsentCount;
        Map<Object, Object> cachedKeyValues = context.cachedKeyValues;
        if (redisAbsentCount == 0) {
            // 所有 key 全部命中缓存
            Map<Object, Object> result = new HashMap<>(cachedKeyValues.size(), 1);
            cachedKeyValues.forEach((k, v) -> result.put(k, fromStoreValue(v)));
            return result;
        }
        // 从 mappingFunction 中获取值
        Map<?, ?> mappingKeyValues = mappingFunction.apply(context.redisAbsentKeys);
        putAll(mappingKeyValues);
        Map<Object, Object> result = new HashMap<>(cachedKeyValues.size() + mappingKeyValues.size(), 1);
        cachedKeyValues.forEach((k, v) -> result.put(k, fromStoreValue(v)));
        result.putAll(mappingKeyValues);
        return result;
    }

    protected void doGetAll(GetAllContext context) {
        context.cachedKeyValues = caffeineCache.getAll(context.allKeys, keyIterable -> {
            Collection<Object> caffeineAbsentKeys = CollUtil.toCollection((Iterable<Object>) keyIterable);
            Collection<Object> redisKeys = CollUtil.trans(caffeineAbsentKeys, this::getRedisKey);
            // 从 redis 批量获取
            List<Object> redisValues = redisTemplate.opsForValue().multiGet(redisKeys);
            Objects.requireNonNull(redisValues);
            // 统计 redis 中没有的 key 数量
            int redisAbsentCount = 0;
            for (Object value : redisValues) {
                if (value == null) {
                    redisAbsentCount++;
                }
            }
            context.redisAbsentCount = redisAbsentCount;
            HashMap<Object, Object> result = new HashMap<>(caffeineAbsentKeys.size() - redisAbsentCount, 1);
            boolean saveCacheAbsentKeys = context.saveRedisAbsentKeys;
            if (saveCacheAbsentKeys) {
                // mappingFunction 的参数
                context.redisAbsentKeys = new HashSet<>(redisAbsentCount);
            }
            int index = 0;
            for (Object key : caffeineAbsentKeys) {
                Object redisValue = redisValues.get(index);
                if (redisValue != null) {
                    result.put(key, redisValue);
                } else if (saveCacheAbsentKeys) {
                    context.redisAbsentKeys.add(key);
                }
                index++;
            }
            return result;
        });
    }

    protected static class GetAllContext {

        public GetAllContext(Iterable<Object> allKeys) {
            this.allKeys = allKeys;
        }

        protected Iterable<Object> allKeys;

        /**
         * 是否将redis未查询到的key保存到 {@link #redisAbsentKeys}
         */
        protected boolean saveRedisAbsentKeys = false;

        /**
         * redis中未查询到的key
         */
        protected Set<Object> redisAbsentKeys;

        /**
         * redis中未查询到的key数量
         */
        protected int redisAbsentCount;

        /**
         * caffeine和redis中缓存的键值，未经过{@link #fromStoreValue}转换
         */
        protected Map<Object, Object> cachedKeyValues;

    }

    @Override
    public void putAll(@NonNull Map<?, ?> map) {
        if (redisEnabled) {
            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> Object execute(@NonNull RedisOperations<K, V> operations) throws DataAccessException {
                    ValueOperations<Object, Object> valueOperations = (ValueOperations<Object, Object>) operations
                            .opsForValue();
                    map.forEach((k, v) -> {
                        Object o = toStoreValue(v);
                        setRedisValue(k, o, getRedisExipre(o), valueOperations);
                        setCaffeineValue(k, o);
                    });
                    return null;
                }
            });
        }
        push(new ArrayList<>(map.keySet()), CacheOperation.EVICT_BATCH);
    }

    @Override
    public void invalidate(@NonNull Object key) {
        evict(key);
    }

    @Override
    public void invalidateAll(@NonNull Iterable<@NonNull ?> keys) {
        Collection<?> keysColl = CollUtil.toCollection(keys);
        Collection<Object> redisKeys = CollUtil.trans(keysColl, this::getRedisKey);
        if (redisEnabled) {
            redisTemplate.delete(redisKeys);
        }
        push(keysColl, CacheOperation.EVICT_BATCH);
        caffeineCache.invalidateAll(keysColl);
    }

    @Override
    public void invalidateAll() {
        this.clear();
    }

    // ---------- 单纯的代理 caffeineCache

    @Override
    public @NonNegative long estimatedSize() {
        return caffeineCache.estimatedSize();
    }

    @Override
    public @NonNull CacheStats stats() {
        return caffeineCache.stats();
    }

    @Override
    public @NonNull ConcurrentMap<@NonNull Object, @NonNull Object> asMap() {
        return caffeineCache.asMap();
    }

    @Override
    public void cleanUp() {
        caffeineCache.cleanUp();
    }

    @Override
    public @NonNull Policy<Object, Object> policy() {
        return caffeineCache.policy();
    }

}
