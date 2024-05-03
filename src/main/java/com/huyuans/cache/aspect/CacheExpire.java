package com.huyuans.cache.aspect;


import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 缓存失效时间注解
 *
 * @author huyuans
 * @date 2024/4/30
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface CacheExpire {

    /**
     * 缓存失效时间
     */
    long value() default 0L;

    /**
     * 单位ms
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;
}
