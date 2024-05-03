package com.huyuans.cache.listener;

import com.huyuans.cache.enums.CacheOperation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 缓存信息实体（用于Redis发布订阅）
 *
 * @author huyuans
 * @date 2024/4/30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheMessage implements Serializable {

    private static final long serialVersionUID = -1L;

    private Object serverId;

    private String cacheName;

    private CacheOperation operation;

    private Object key;

}
