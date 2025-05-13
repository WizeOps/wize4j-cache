package net.wizeops.wize4j.cache.factory;

import lombok.extern.slf4j.Slf4j;
import net.wizeops.wize4j.cache.config.CacheConfiguration;
import net.wizeops.wize4j.cache.manager.CacheManager;

@Slf4j
public class CacheFactory {
    public static CacheManager createCacheManager(CacheConfiguration config) {
        return new CacheManager(config);
    }
}

