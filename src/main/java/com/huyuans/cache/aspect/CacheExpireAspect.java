package com.huyuans.cache.aspect;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import com.huyuans.cache.utils.CacheHolder;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.SpringCacheAnnotationParser;
import org.springframework.cache.interceptor.CacheOperation;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 缓存失效时间切面
 *
 * @author huyuans
 * @date 2024/4/30
 */
@Aspect
public class CacheExpireAspect implements Ordered {

    SpringCacheAnnotationParser annotationParser = new SpringCacheAnnotationParser() {
        @Override
        public boolean isCandidateClass(Class<?> targetClass) {
            return AnnotationUtils.isCandidateClass(targetClass, ListUtil.of(Cacheable.class, CachePut.class, Caching.class));
        }
    };

    @SneakyThrows
    @Around("@annotation(cacheExpire)")
    public Object around(ProceedingJoinPoint point, CacheExpire cacheExpire) {
        Duration duration = Duration.ofNanos(cacheExpire.timeUnit().toNanos(cacheExpire.value()));

        MethodSignature signature = (MethodSignature) point.getSignature();

        Collection<CacheOperation> cacheOperations = annotationParser.parseCacheAnnotations(signature.getMethod());
        if(CollUtil.isEmpty(cacheOperations)){
            return point.proceed();
        }
        Set<String> cacheNames = new HashSet<>();
        cacheOperations.forEach(cacheOperation -> cacheNames.addAll(cacheOperation.getCacheNames()));
        if(CollUtil.isEmpty(cacheNames)){
            return point.proceed();
        }
        try {
            for (String cacheName : cacheNames) {
                CacheHolder.setExpireTime(cacheName, duration);
            }
            return point.proceed();
        } finally {
            for (String cacheName : cacheNames) {
                CacheHolder.remove(cacheName);
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
