package com.huyuans.cache.listener;

import cn.hutool.core.util.StrUtil;
import com.huyuans.cache.RedisCaffeineCacheManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

/**
 * 过期键事件通知
 *
 * @author huyuans
 * @date 2024/5/2
 */
@RequiredArgsConstructor
public class CacheExpiredListener implements MessageListener {

    private final RedisCaffeineCacheManager redisCaffeineCacheManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        int index = body.lastIndexOf(":");
        if (index < 0) {
            return;
        }
        String key = body.substring(index + 1);
        if (StrUtil.isBlank(key)) {
            return;
        }
        String cacheName = body.substring(0, index);
        redisCaffeineCacheManager.clearLocal(cacheName, key);
    }

}
