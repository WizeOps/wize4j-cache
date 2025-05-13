package net.wizeops.wize4j.cache.providers.memory;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.wizeops.wize4j.cache.api.CacheProvider;
import net.wizeops.wize4j.cache.compression.CompressionStrategy;
import net.wizeops.wize4j.cache.compression.DefaultCompressionStrategy;
import net.wizeops.wize4j.cache.config.CacheConfiguration;
import net.wizeops.wize4j.cache.core.CacheEntry;
import net.wizeops.wize4j.cache.core.CacheStatistics;
import net.wizeops.wize4j.cache.exceptions.CacheException;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


@Slf4j
public class InMemoryCacheProvider implements CacheProvider {
    private final Map<String, CacheEntry> entries;
    private final CacheConfiguration config;
    @Getter
    private final CacheStatistics statistics;
    private final CompressionStrategy compressionStrategy;
    private final AtomicLong lastCleanupTime = new AtomicLong(System.currentTimeMillis());

    public InMemoryCacheProvider(CacheConfiguration config) {
        this.config = config;
        this.entries = new ConcurrentHashMap<>();
        this.statistics = config.isEnableStatistics() ? new CacheStatistics() : null;
        this.compressionStrategy = config.isEnableCompression() ?
                new DefaultCompressionStrategy() : null;
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        validateInputs(key, value, ttl);

        if (entries.size() >= config.getMaxSize()) {
            evictOldest();
        }

        // Check for cleanup
        if (shouldRunCleanup()) {
            removeExpired();
        }

        Object valueToStore = value;
        boolean compressed = false;

        // Compress if needed
        if (shouldCompress(value)) {
            valueToStore = compressionStrategy.compress(value);
            compressed = true;
        }

        long expirationTime = System.currentTimeMillis() + ttl.toMillis();
        entries.put(key, new CacheEntry(valueToStore, expirationTime, compressed));

        if (statistics != null) {
            statistics.recordPut();
        }
    }

    @Override
    public Object get(String key) {
        if (key == null) {
            return null;
        }

        CacheEntry entry = entries.get(key);
        if (entry == null) {
            if (statistics != null) {
                statistics.recordMiss();
            }
            return null;
        }

        if (entry.isExpired()) {
            entries.remove(key);
            if (statistics != null) {
                statistics.recordEviction();
            }
            return null;
        }

        entry.recordAccess();
        if (statistics != null) {
            statistics.recordHit();
        }

        Object value = entry.getValue();

        // Decompress if needed
        if (entry.isCompressed() && compressionStrategy != null) {
            value = compressionStrategy.decompress((byte[]) value);
        }

        return value;
    }

    @Override
    public void evict(String key) {
        if (key != null && entries.remove(key) != null && statistics != null) {
            statistics.recordEviction();
        }
    }

    @Override
    public void clear() {
        int size = entries.size();
        entries.clear();
        if (statistics != null && size > 0) {
            statistics.recordClear(size);
        }
    }

    @Override
    public void removeExpired() {
        int count = 0;
        for (Map.Entry<String, CacheEntry> entry : entries.entrySet()) {
            if (entry.getValue().isExpired()) {
                entries.remove(entry.getKey());
                count++;
            }
        }
        if (statistics != null && count > 0) {
            statistics.recordBulkEviction(count);
        }
        lastCleanupTime.set(System.currentTimeMillis());
    }

    @Override
    public void close() {
        clear();
    }

    @Override
    public String getProviderName() {
        return "InMemory";
    }

    private void validateInputs(String key, Object value, Duration ttl) {
        if (key == null) {
            throw new CacheException("Cache key cannot be null");
        }
        if (value == null) {
            throw new CacheException("Cache value cannot be null");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new CacheException("TTL must be positive");
        }
    }

    private void evictOldest() {
        if (entries.isEmpty()) {
            return;
        }

        entries.entrySet().stream()
                .min(Comparator.comparingLong(e -> e.getValue().getLastAccessTime().get()))
                .map(Map.Entry::getKey)
                .ifPresent(this::evict);
    }

    private boolean shouldCompress(Object value) {
        if (!config.isEnableCompression() || compressionStrategy == null) {
            return false;
        }
        try {
            return estimateSize(value) > config.getCompressionThresholdBytes();
        } catch (Exception e) {
            log.warn("Failed to estimate object size for compression", e);
            return false;
        }
    }

    private long estimateSize(Object obj) {
        // Réutiliser le code de CacheUtil pour estimer la taille
        return 0; // À implémenter
    }

    private boolean shouldRunCleanup() {
        return System.currentTimeMillis() - lastCleanupTime.get() >
                Duration.ofMinutes(config.getCleanupIntervalMinutes()).toMillis();
    }
}

