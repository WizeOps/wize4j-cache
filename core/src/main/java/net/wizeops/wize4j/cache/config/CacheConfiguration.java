package net.wizeops.wize4j.cache.config;

import lombok.Builder;
import lombok.Data;
import net.wizeops.wize4j.cache.api.CacheProvider;

import java.util.List;

@Data
@Builder
public class CacheConfiguration {
    @Builder.Default
    private CacheProviderType providerType = CacheProviderType.IN_MEMORY;

    private CacheProvider customProvider;

    // Configuration de base
    @Builder.Default
    private long defaultTtlSeconds = 3600;

    @Builder.Default
    private int maxSize = 10000;

    @Builder.Default
    private int cleanupIntervalMinutes = 5;

    // Compression
    @Builder.Default
    private boolean enableCompression = false;

    @Builder.Default
    private long compressionThresholdBytes = 1024;

    // Statistiques
    @Builder.Default
    private boolean enableStatistics = true;

    // Redis
    private String redisHost;
    private int redisPort;
    private String redisPassword;
    private int redisDatabase;

    // Hazelcast
    private List<String> hazelcastMembers;
    private String hazelcastGroupName;
    private String hazelcastConfigPath;

    // EhCache
    private String ehcacheConfigPath;
    private boolean diskPersistence;
    private String diskStorePath;
}





