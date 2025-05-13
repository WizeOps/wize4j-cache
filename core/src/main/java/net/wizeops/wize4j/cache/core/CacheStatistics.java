package net.wizeops.wize4j.cache.core;

import lombok.Getter;

import java.util.concurrent.atomic.AtomicLong;

@Getter
public class CacheStatistics {
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong puts = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final AtomicLong clearCount = new AtomicLong();
    private final AtomicLong size = new AtomicLong();
    private final AtomicLong totalLoadTime = new AtomicLong();

    public double getHitRatio() {
        long totalRequests = hits.get() + misses.get();
        return totalRequests == 0 ? 0 : (double) hits.get() / totalRequests;
    }

    public void recordHit() {
        hits.incrementAndGet();
    }

    public void recordMiss() {
        misses.incrementAndGet();
    }

    public void recordPut() {
        puts.incrementAndGet();
    }

    public void recordEviction() {
        evictions.incrementAndGet();
    }

    public void recordClear(int size) {
        clearCount.incrementAndGet();
        evictions.addAndGet(size);
    }

    public void recordBulkEviction(int count) {
        evictions.addAndGet(count);
    }
}



