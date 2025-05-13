package net.wizeops.wize4j.cache.api;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

public interface RedisCacheOperations extends CacheProvider {

    Long increment(String key, long delta);

    boolean expire(String key, Duration ttl);

    Set<String> keys(String pattern);

    boolean ping();

    void hset(String key, String field, Object value);

    Object hget(String key, String field);

    Map<String, Object> hgetAll(String key);
}

