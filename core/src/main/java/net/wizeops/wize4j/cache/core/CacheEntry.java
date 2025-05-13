package net.wizeops.wize4j.cache.core;

import lombok.Getter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Getter
public class CacheEntry {
    private final Object value;
    private final long expirationTime;
    private final AtomicLong lastAccessTime;
    private final AtomicInteger accessCount;
    private final boolean compressed;

    public CacheEntry(Object value, long expirationTime, boolean compressed) {
        this.value = value;
        this.expirationTime = expirationTime;
        this.lastAccessTime = new AtomicLong(System.currentTimeMillis());
        this.accessCount = new AtomicInteger(0);
        this.compressed = compressed;
    }

    public CacheEntry(Object value, long expirationTime) {
        this(value, expirationTime, false);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }

    public void recordAccess() {
        lastAccessTime.set(System.currentTimeMillis());
        accessCount.incrementAndGet();
    }
}





