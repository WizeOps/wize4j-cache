package net.wizeops.wize4j.cache.manager;

import lombok.extern.slf4j.Slf4j;
import net.wizeops.wize4j.cache.api.CacheProvider;
import net.wizeops.wize4j.cache.config.CacheConfiguration;
import net.wizeops.wize4j.cache.core.CacheStatistics;
import net.wizeops.wize4j.cache.exceptions.CacheException;
import net.wizeops.wize4j.cache.providers.memory.InMemoryCacheProvider;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CacheManager implements AutoCloseable {
    private final Map<String, CacheProvider> cacheProviders;
    private final CacheConfiguration config;
    private final ScheduledExecutorService cleanupExecutor;

    public CacheManager(CacheConfiguration config) {
        this.config = config;
        this.cacheProviders = new ConcurrentHashMap<>();
        this.cleanupExecutor = createAndStartCleanupExecutor();
    }

    public void put(String cacheName, String key, Object value, Duration ttl) {
        validateInputs(cacheName, key, value);
        Duration effectiveTtl = ttl != null ? ttl : Duration.ofSeconds(config.getDefaultTtlSeconds());

        try {
            getCacheProvider(cacheName).put(key, value, effectiveTtl);
            log.debug("Put value in cache '{}' with key: {}", cacheName, key);
        } catch (Exception e) {
            log.error("Error putting value in cache '{}' with key: {}", cacheName, key, e);
            throw new CacheException("Failed to put value in cache", e);
        }
    }

    public Object get(String cacheName, String key) {
        validateInputs(cacheName, key);

        try {
            if (!cacheProviders.containsKey(cacheName)) {
                log.debug("Cache not found: {}", cacheName);
                return null;
            }

            Object value = getCacheProvider(cacheName).get(key);
            log.debug("Get value from cache '{}' with key: {} - {}",
                    cacheName, key, value != null ? "HIT" : "MISS");
            return value;
        } catch (Exception e) {
            log.error("Error getting value from cache '{}' with key: {}", cacheName, key, e);
            throw new CacheException("Failed to get value from cache", e);
        }
    }

    public void evict(String cacheName, String key) {
        validateInputs(cacheName, key);

        try {
            if (cacheProviders.containsKey(cacheName)) {
                getCacheProvider(cacheName).evict(key);
                log.debug("Evicted key: {} from cache: {}", key, cacheName);
            }
        } catch (Exception e) {
            log.error("Error evicting key from cache '{}': {}", cacheName, key, e);
            throw new CacheException("Failed to evict key from cache", e);
        }
    }

    public void evictAll(String cacheName) {
        if (cacheName == null) {
            throw new CacheException("Cache name cannot be null");
        }

        try {
            if (cacheProviders.containsKey(cacheName)) {
                getCacheProvider(cacheName).clear();
                cacheProviders.remove(cacheName);
                log.debug("Evicted all entries from cache: {}", cacheName);
            }
        } catch (Exception e) {
            log.error("Error evicting all entries from cache: {}", cacheName, e);
            throw new CacheException("Failed to evict all entries from cache", e);
        }
    }

    public CacheStatistics getStatistics(String cacheName) {
        if (!config.isEnableStatistics()) {
            throw new CacheException("Statistics are not enabled");
        }

        if (!cacheProviders.containsKey(cacheName)) {
            return null;
        }

        return getCacheProvider(cacheName).getStatistics();
    }

    /**
     * Accès typé aux providers pour les fonctionnalités spécifiques
     */
    @SuppressWarnings("unchecked")
    public <T extends CacheProvider> T getTypedCacheProvider(String cacheName, Class<T> type) {
        CacheProvider provider = getCacheProvider(cacheName);
        if (type.isInstance(provider)) {
            return (T) provider;
        }
        throw new CacheException("Cache provider for '" + cacheName + "' is not of type " + type.getSimpleName());
    }

    @Override
    public void close() {
        try {
            log.info("Shutting down cache manager");
            cleanupExecutor.shutdown();
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }

            // Fermer tous les providers
            for (Map.Entry<String, CacheProvider> entry : cacheProviders.entrySet()) {
                try {
                    entry.getValue().close();
                } catch (Exception e) {
                    log.warn("Error closing cache provider for '{}'", entry.getKey(), e);
                }
            }

            cacheProviders.clear();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanupExecutor.shutdownNow();
        }
    }

    private ScheduledExecutorService createAndStartCleanupExecutor() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "cache-cleanup-thread");
            thread.setDaemon(true);
            return thread;
        });

        executor.scheduleAtFixedRate(
                this::cleanup,
                config.getCleanupIntervalMinutes(),
                config.getCleanupIntervalMinutes(),
                TimeUnit.MINUTES
        );

        return executor;
    }

    private void cleanup() {
        try {
            log.debug("Starting cache cleanup");
            cacheProviders.forEach((name, provider) -> {
                try {
                    provider.removeExpired();
                    log.debug("Cleaned up cache: {}", name);
                } catch (Exception e) {
                    log.error("Error cleaning up cache: {}", name, e);
                }
            });
        } catch (Exception e) {
            log.error("Error during cache cleanup", e);
        }
    }

    private CacheProvider getCacheProvider(String cacheName) {
        return cacheProviders.computeIfAbsent(cacheName, name -> createCacheProvider());
    }

    private CacheProvider createCacheProvider() {
        try {
            return switch (config.getProviderType()) {
                case IN_MEMORY -> new InMemoryCacheProvider(config);
                case REDIS -> createRedisProvider();
                case HAZELCAST -> createHazelcastProvider();
                case EHCACHE -> createEhCacheProvider();
                case CUSTOM -> {
                    if (config.getCustomProvider() == null) {
                        throw new CacheException("Custom provider is null");
                    }
                    yield config.getCustomProvider();
                }
            };
        } catch (ClassNotFoundException e) {
            throw new CacheException("Cache provider implementation not found", e);
        } catch (Exception e) {
            throw new CacheException("Failed to create cache provider", e);
        }
    }

    private CacheProvider createRedisProvider() throws ClassNotFoundException {
        // Vérification dynamique de la présence de la classe Redis
        Class.forName("net.wizeops.wize4j.cache.providers.redis.RedisCacheProvider");
        return new RedisCacheProvider(config);
    }

    private CacheProvider createHazelcastProvider() throws ClassNotFoundException {
        // Vérification dynamique de la présence de la classe Hazelcast
        Class.forName("net.wizeops.wize4j.cache.providers.hazelcast.HazelcastCacheProvider");
        return new HazelcastCacheProvider(config);
    }

    private CacheProvider createEhCacheProvider() throws ClassNotFoundException {
        // Vérification dynamique de la présence de la classe EhCache
        Class.forName("net.wizeops.wize4j.cache.providers.ehcache.EhCacheProvider");
        return new EhCacheProvider(config);
    }

    private void validateInputs(String cacheName, String key) {
        validateInputs(cacheName, key, null);
    }

    private void validateInputs(String cacheName, String key, Object value) {
        if (cacheName == null) {
            throw new CacheException("Cache name cannot be null");
        }
        if (key == null) {
            throw new CacheException("Key cannot be null");
        }
        if (value == null) {
            log.debug("Null value provided for key: {} in cache: {}", key, cacheName);
        }
    }
}





