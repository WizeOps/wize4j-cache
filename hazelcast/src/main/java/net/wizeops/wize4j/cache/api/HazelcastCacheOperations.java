package net.wizeops.wize4j.cache.api;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public interface HazelcastCacheOperations extends CacheProvider {
    String addMapListener(Object listenerObject);

    boolean removeMapListener(String registrationId);

    Object getNativeMap();

    Set<String> getClusterMembers();

    void lock(String key, long leaseTime, TimeUnit timeUnit);

    void unlock(String key);

    <R> R compute(String key, Function<Object, R> mappingFunction);

    void executeOnEntries(Object task);

    Map<String, Object> executeQuery(Object predicate);
}

