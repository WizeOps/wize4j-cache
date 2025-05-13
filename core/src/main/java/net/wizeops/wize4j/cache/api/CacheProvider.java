package net.wizeops.wize4j.cache.api;

import net.wizeops.wize4j.cache.core.CacheStatistics;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public interface CacheProvider extends AutoCloseable {
    void put(String key, Object value, Duration ttl);

    Object get(String key);

    void evict(String key);

    void clear();

    void removeExpired();

    CacheStatistics getStatistics();

    String getProviderName();

    default boolean containsKey(String key) {
        return get(key) != null;
    }

    default boolean putIfAbsent(String key, Object value, Duration ttl) {
        if (!containsKey(key)) {
            put(key, value, ttl);
            return true;
        }
        return false;
    }

    default Map<String, Object> getBulk(Collection<String> keys) {
        return keys.stream()
                .collect(Collectors.toMap(
                        key -> key,
                        this::get,
                        (v1, v2) -> v1
                ));
    }
}


