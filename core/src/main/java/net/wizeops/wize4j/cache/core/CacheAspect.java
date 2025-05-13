package net.wizeops.wize4j.cache.core;

import lombok.extern.slf4j.Slf4j;
import net.wizeops.wize4j.cache.annotations.CacheEvict;
import net.wizeops.wize4j.cache.annotations.Cacheable;
import net.wizeops.wize4j.cache.manager.CacheManager;
import net.wizeops.wize4j.cache.utils.KeyGenerator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.time.Duration;

@Slf4j
@Aspect
public class CacheAspect {
    private final CacheManager cacheManager;

    public CacheAspect(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Around("@annotation(cacheable)")
    public Object cacheable(ProceedingJoinPoint joinPoint, Cacheable cacheable) throws Throwable {
        String cacheKey = KeyGenerator.generateKey(joinPoint, cacheable.key());
        Object cachedValue = cacheManager.get(cacheable.value(), cacheKey);

        if (cachedValue != null) {
            log.debug("Cache hit for key: {}", cacheKey);
            return cachedValue;
        }

        log.debug("Cache miss for key: {}", cacheKey);
        Object result = joinPoint.proceed();

        if (result != null) {
            cacheManager.put(cacheable.value(), cacheKey, result,
                    Duration.ofSeconds(cacheable.ttlSeconds()));
            log.debug("Cached result for key: {}", cacheKey);
        }

        return result;
    }

    @Around("@annotation(cacheEvict)")
    public Object cacheEvict(ProceedingJoinPoint joinPoint, CacheEvict cacheEvict) throws Throwable {
        if (cacheEvict.key().isEmpty()) {
            log.debug("Evicting all entries from cache: {}", cacheEvict.value());
            cacheManager.evictAll(cacheEvict.value());
        } else {
            String cacheKey = KeyGenerator.generateKey(joinPoint, cacheEvict.key());
            log.debug("Evicting entry with key: {} from cache: {}", cacheKey, cacheEvict.value());
            cacheManager.evict(cacheEvict.value(), cacheKey);
        }

        return joinPoint.proceed();
    }
}


