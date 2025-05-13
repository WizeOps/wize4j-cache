package net.wizeops.wize4j.cache.providers.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.MapEvent;
import com.hazelcast.map.listener.MapListener;
import com.hazelcast.query.Predicate;
import lombok.extern.slf4j.Slf4j;
import net.wizeops.wize4j.cache.api.HazelcastCacheOperations;
import net.wizeops.wize4j.cache.config.CacheConfiguration;
import net.wizeops.wize4j.cache.core.CacheStatistics;
import net.wizeops.wize4j.cache.exceptions.CacheException;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class HazelcastCacheProvider implements HazelcastCacheOperations {
    private final HazelcastInstance hazelcastInstance;
    private final String mapName;
    private final IMap<String, Object> cacheMap;
    private final CacheConfiguration config;
    private final AtomicReference<CacheStatistics> statistics = new AtomicReference<>(new CacheStatistics());

    private final Map<String, UUID> registeredListeners = new ConcurrentHashMap<>();

    public HazelcastCacheProvider(CacheConfiguration config) {
        this.config = config;
        this.mapName = "wize4j-cache";

        try {
            this.hazelcastInstance = createHazelcastInstance(config);
            this.cacheMap = hazelcastInstance.getMap(mapName);

            if (config.isEnableStatistics()) {
                addStatisticsListener();
            }

            log.info("Hazelcast cache provider initialized with map name: {}", mapName);
            log.info("Hazelcast cluster members: {}", getClusterMembers());
        } catch (Exception e) {
            log.error("Failed to initialize Hazelcast cache provider", e);
            throw new CacheException("Failed to initialize Hazelcast cache provider", e);
        }
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        if (key == null) {
            throw new CacheException("Key cannot be null");
        }
        if (value == null) {
            throw new CacheException("Value cannot be null");
        }

        try {
            // Stocker avec TTL
            cacheMap.put(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);

            log.debug("Added entry to Hazelcast cache: {}", key);
        } catch (Exception e) {
            log.error("Error adding entry to Hazelcast cache: {}", key, e);
            throw new CacheException("Failed to add entry to Hazelcast cache", e);
        }
    }

    @Override
    public Object get(String key) {
        if (key == null) {
            return null;
        }

        try {
            Object value = cacheMap.get(key);

            if (value == null) {
                if (config.isEnableStatistics()) {
                    statistics.get().recordMiss();
                }
                log.debug("Cache miss for key: {}", key);
                return null;
            }

            if (config.isEnableStatistics()) {
                statistics.get().recordHit();
            }

            log.debug("Cache hit for key: {}", key);
            return value;
        } catch (Exception e) {
            log.error("Error retrieving entry from Hazelcast cache: {}", key, e);
            throw new CacheException("Failed to retrieve entry from Hazelcast cache", e);
        }
    }

    @Override
    public void evict(String key) {
        if (key == null) {
            return;
        }

        try {
            cacheMap.remove(key);
            log.debug("Evicted key from Hazelcast cache: {}", key);
        } catch (Exception e) {
            log.error("Error evicting key from Hazelcast cache: {}", key, e);
            throw new CacheException("Failed to evict key from Hazelcast cache", e);
        }
    }

    @Override
    public void clear() {
        try {
            int size = cacheMap.size();
            cacheMap.clear();

            if (config.isEnableStatistics()) {
                statistics.get().recordClear(size);
            }

            log.debug("Cleared all entries from Hazelcast cache map: {}", mapName);
        } catch (Exception e) {
            log.error("Error clearing Hazelcast cache", e);
            throw new CacheException("Failed to clear Hazelcast cache", e);
        }
    }

    @Override
    public void removeExpired() {
        log.debug("Hazelcast automatically manages entry expiration");
    }

    @Override
    public CacheStatistics getStatistics() {
        return statistics.get();
    }

    @Override
    public String getProviderName() {
        return "Hazelcast";
    }

    @Override
    public void close() {
        try {
            for (Map.Entry<String, UUID> entry : registeredListeners.entrySet()) {
                cacheMap.removeEntryListener(entry.getValue());
            }
            registeredListeners.clear();

            hazelcastInstance.shutdown();
            log.info("Hazelcast cache provider closed");
        } catch (Exception e) {
            log.error("Error closing Hazelcast cache provider", e);
        }
    }

    @Override
    public String addMapListener(Object listenerObject) {
        if (listenerObject == null) {
            throw new CacheException("Listener object cannot be null");
        }

        try {
            if (!(listenerObject instanceof MapListener)) {
                throw new CacheException("Listener must implement MapListener interface");
            }

            UUID listenerId = cacheMap.addEntryListener((MapListener) listenerObject, true);
            String randomKey = UUID.randomUUID().toString();
            registeredListeners.put(randomKey, listenerId);

            log.debug("Added map listener to Hazelcast cache map: {}", mapName);
            return randomKey;
        } catch (Exception e) {
            log.error("Error adding map listener to Hazelcast cache", e);
            throw new CacheException("Failed to add map listener to Hazelcast cache", e);
        }
    }

    @Override
    public boolean removeMapListener(String registrationId) {
        if (registrationId == null) {
            return false;
        }

        try {
            UUID listenerId = registeredListeners.remove(registrationId);
            if (listenerId != null) {
                boolean removed = cacheMap.removeEntryListener(listenerId);
                log.debug("Removed map listener from Hazelcast cache map: {}, success: {}", mapName, removed);
                return removed;
            }
            return false;
        } catch (Exception e) {
            log.error("Error removing map listener from Hazelcast cache", e);
            throw new CacheException("Failed to remove map listener from Hazelcast cache", e);
        }
    }

    @Override
    public Object getNativeMap() {
        return cacheMap;
    }

    @Override
    public Set<String> getClusterMembers() {
        try {
            return hazelcastInstance.getCluster().getMembers().stream()
                    .map(member -> member.getAddress().toString())
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Error getting Hazelcast cluster members", e);
            throw new CacheException("Failed to get Hazelcast cluster members", e);
        }
    }

    @Override
    public void lock(String key, long leaseTime, TimeUnit timeUnit) {
        if (key == null) {
            throw new CacheException("Key cannot be null for lock operation");
        }

        try {
            if (leaseTime > 0) {
                cacheMap.lock(key, leaseTime, timeUnit);
            } else {
                cacheMap.lock(key);
            }
            log.debug("Locked key in Hazelcast cache: {}", key);
        } catch (Exception e) {
            log.error("Error locking key in Hazelcast cache: {}", key, e);
            throw new CacheException("Failed to lock key in Hazelcast cache", e);
        }
    }

    @Override
    public void unlock(String key) {
        if (key == null) {
            throw new CacheException("Key cannot be null for unlock operation");
        }

        try {
            cacheMap.unlock(key);
            log.debug("Unlocked key in Hazelcast cache: {}", key);
        } catch (Exception e) {
            log.error("Error unlocking key in Hazelcast cache: {}", key, e);
            throw new CacheException("Failed to unlock key in Hazelcast cache", e);
        }
    }

    @Override
    public <R> R compute(String key, Function<Object, R> mappingFunction) {
        if (key == null || mappingFunction == null) {
            throw new CacheException("Key and mapping function cannot be null for compute operation");
        }

        try {
            cacheMap.lock(key);
            try {
                Object currentValue = cacheMap.get(key);
                R result = mappingFunction.apply(currentValue);

                if (result != null) {
                    cacheMap.put(key, result);
                } else {
                    cacheMap.remove(key);
                }

                return result;
            } finally {
                cacheMap.unlock(key);
            }
        } catch (Exception e) {
            log.error("Error computing value for key in Hazelcast cache: {}", key, e);
            throw new CacheException("Failed to compute value for key in Hazelcast cache", e);
        }
    }

    @Override
    public void executeOnEntries(Object task) {
        if (task == null) {
            throw new CacheException("Task cannot be null for executeOnEntries operation");
        }

        try {
            if (!(task instanceof com.hazelcast.map.EntryProcessor)) {
                throw new CacheException("Task must implement EntryProcessor interface");
            }

            cacheMap.executeOnEntries((com.hazelcast.map.EntryProcessor) task);
            log.debug("Executed task on all entries in Hazelcast cache map: {}", mapName);
        } catch (Exception e) {
            log.error("Error executing task on entries in Hazelcast cache", e);
            throw new CacheException("Failed to execute task on entries in Hazelcast cache", e);
        }
    }

    @Override
    public Map<String, Object> executeQuery(Object predicate) {
        if (predicate == null) {
            throw new CacheException("Predicate cannot be null for executeQuery operation");
        }

        try {
            if (!(predicate instanceof Predicate)) {
                throw new CacheException("Predicate must be of type Predicate");
            }

            Set<Map.Entry<String, Object>> entries = cacheMap.entrySet((Predicate) predicate);

            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : entries) {
                result.put(entry.getKey(), entry.getValue());
            }

            log.debug("Executed query on Hazelcast cache map: {}, result size: {}", mapName, result.size());
            return result;
        } catch (Exception e) {
            log.error("Error executing query on Hazelcast cache", e);
            throw new CacheException("Failed to execute query on Hazelcast cache", e);
        }
    }

    private HazelcastInstance createHazelcastInstance(CacheConfiguration cacheConfig) {
        try {
            if (cacheConfig.getHazelcastConfigPath() != null && !cacheConfig.getHazelcastConfigPath().isEmpty()) {
                Path configPath = Paths.get(cacheConfig.getHazelcastConfigPath());

                if (!Files.exists(configPath)) {
                    throw new FileNotFoundException("Hazelcast configuration file not found: " + configPath);
                }

                ClientConfig clientConfig = new XmlClientConfigBuilder(configPath.toFile()).build();
                return HazelcastClient.newHazelcastClient(clientConfig);
            }

            ClientConfig clientConfig = new ClientConfig();

            if (cacheConfig.getHazelcastMembers() != null && !cacheConfig.getHazelcastMembers().isEmpty()) {
                clientConfig.getNetworkConfig().setAddresses(cacheConfig.getHazelcastMembers());
            }

            if (cacheConfig.getHazelcastGroupName() != null && !cacheConfig.getHazelcastGroupName().isEmpty()) {
                clientConfig.setClusterName(cacheConfig.getHazelcastGroupName());
            }

            return HazelcastClient.newHazelcastClient(clientConfig);
        } catch (Exception e) {
            log.error("Failed to create Hazelcast instance", e);
            throw new CacheException("Failed to create Hazelcast instance", e);
        }
    }

    private void addStatisticsListener() {
        try {
            EntryListener<String, Object> statsListener = new EntryListener<>() {
                @Override
                public void entryAdded(EntryEvent<String, Object> event) {
                    statistics.get().recordPut();
                }

                @Override
                public void entryRemoved(EntryEvent<String, Object> event) {
                    statistics.get().recordEviction();
                }

                @Override
                public void entryUpdated(EntryEvent<String, Object> event) {
                    statistics.get().recordPut();
                }

                @Override
                public void entryExpired(EntryEvent<String, Object> event) {
                    statistics.get().recordEviction();
                }

                @Override
                public void entryEvicted(EntryEvent<String, Object> event) {
                    statistics.get().recordEviction();
                }

                @Override
                public void mapCleared(MapEvent event) {
                    statistics.get().recordClear((int) event.getNumberOfEntriesAffected());
                }

                @Override
                public void mapEvicted(MapEvent event) {
                    statistics.get().recordBulkEviction((int) event.getNumberOfEntriesAffected());
                }
            };

            UUID listenerId = cacheMap.addEntryListener(statsListener, false);
            String randomKey = "statistics-listener";
            registeredListeners.put(randomKey, listenerId);

            log.debug("Added statistics listener to Hazelcast cache map: {}", mapName);
        } catch (Exception e) {
            log.error("Error adding statistics listener to Hazelcast cache", e);
        }
    }
}