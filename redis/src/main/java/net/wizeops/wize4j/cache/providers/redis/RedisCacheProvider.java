package net.wizeops.wize4j.cache.providers.redis;

import lombok.extern.slf4j.Slf4j;
import net.wizeops.wize4j.cache.api.RedisCacheOperations;
import net.wizeops.wize4j.cache.config.CacheConfiguration;
import net.wizeops.wize4j.cache.core.CacheStatistics;
import net.wizeops.wize4j.cache.exceptions.CacheException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.SetParams;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
public class RedisCacheProvider implements RedisCacheOperations {
    private final JedisPool jedisPool;
    private final CacheConfiguration config;
    private final AtomicReference<CacheStatistics> statistics = new AtomicReference<>(new CacheStatistics());
    private final String keyPrefix;

    public RedisCacheProvider(CacheConfiguration config) {
        this.config = config;
        this.keyPrefix = "wize4j:cache:";

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(100);
        poolConfig.setMaxIdle(20);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);

        this.jedisPool = new JedisPool(
                poolConfig,
                config.getRedisHost() != null ? config.getRedisHost() : "localhost",
                config.getRedisPort() > 0 ? config.getRedisPort() : 6379,
                2000, // timeout
                config.getRedisPassword(),
                Math.max(config.getRedisDatabase(), 0)
        );

        try (Jedis jedis = jedisPool.getResource()) {
            if (!jedis.ping().equalsIgnoreCase("PONG")) {
                throw new CacheException("Cannot connect to Redis server");
            }
            log.info("Connected to Redis server: {}:{} database: {}",
                    config.getRedisHost(), config.getRedisPort(), config.getRedisDatabase());
        } catch (Exception e) {
            log.error("Failed to connect to Redis", e);
            throw new CacheException("Failed to connect to Redis server", e);
        }
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        if (key == null || value == null) {
            return;
        }

        String redisKey = formatKey(key);

        try (Jedis jedis = jedisPool.getResource()) {
            byte[] serialized = serialize(value);

            SetParams params = new SetParams();
            params.ex(ttl.getSeconds());

            jedis.set(redisKey.getBytes(), serialized, params);

            if (config.isEnableStatistics()) {
                statistics.get().recordPut();
            }
            log.debug("Stored value in Redis for key: {}", key);
        } catch (Exception e) {
            log.error("Error storing value in Redis for key: {}", key, e);
            throw new CacheException("Failed to store value in Redis", e);
        }
    }

    @Override
    public Object get(String key) {
        if (key == null) {
            return null;
        }

        String redisKey = formatKey(key);

        try (Jedis jedis = jedisPool.getResource()) {
            byte[] data = jedis.get(redisKey.getBytes());

            if (data == null) {
                if (config.isEnableStatistics()) {
                    statistics.get().recordMiss();
                }
                log.debug("Cache miss for key: {}", key);
                return null;
            }

            if (config.isEnableStatistics()) {
                statistics.get().recordHit();
            }

            Object value = deserialize(data);
            log.debug("Cache hit for key: {}", key);
            return value;
        } catch (Exception e) {
            log.error("Error retrieving value from Redis for key: {}", key, e);
            throw new CacheException("Failed to retrieve value from Redis", e);
        }
    }

    @Override
    public void evict(String key) {
        if (key == null) {
            return;
        }

        String redisKey = formatKey(key);

        try (Jedis jedis = jedisPool.getResource()) {
            long removed = jedis.del(redisKey);
            if (removed > 0 && config.isEnableStatistics()) {
                statistics.get().recordEviction();
                log.debug("Evicted key from Redis: {}", key);
            }
        } catch (Exception e) {
            log.error("Error removing key from Redis: {}", key, e);
            throw new CacheException("Failed to remove key from Redis", e);
        }
    }

    @Override
    public void clear() {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys(keyPrefix + "*");
            if (!keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
                if (config.isEnableStatistics()) {
                    statistics.get().recordClear(keys.size());
                }
                log.debug("Cleared all keys from Redis cache");
            }
        } catch (Exception e) {
            log.error("Error clearing Redis cache", e);
            throw new CacheException("Failed to clear Redis cache", e);
        }
    }

    @Override
    public void removeExpired() {
        log.debug("Redis automatically manages key expiration");
    }

    @Override
    public void close() {
        jedisPool.close();
        log.info("Redis cache provider closed");
    }

    @Override
    public CacheStatistics getStatistics() {
        return statistics.get();
    }

    @Override
    public String getProviderName() {
        return "Redis";
    }

    @Override
    public Long increment(String key, long delta) {
        if (key == null) {
            throw new CacheException("Key cannot be null for increment operation");
        }

        String redisKey = formatKey(key);

        try (Jedis jedis = jedisPool.getResource()) {
            Long newValue = jedis.incrBy(redisKey, delta);
            log.debug("Incremented key: {} by {}, new value: {}", key, delta, newValue);
            return newValue;
        } catch (Exception e) {
            log.error("Error incrementing value in Redis for key: {}", key, e);
            throw new CacheException("Failed to increment value in Redis", e);
        }
    }

    @Override
    public boolean expire(String key, Duration ttl) {
        if (key == null) {
            throw new CacheException("Key cannot be null for expire operation");
        }

        String redisKey = formatKey(key);

        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.expire(redisKey, ttl.getSeconds());
            boolean success = result == 1;
            log.debug("Set expiration for key: {} to {} seconds, success: {}",
                    key, ttl.getSeconds(), success);
            return success;
        } catch (Exception e) {
            log.error("Error setting expiration in Redis for key: {}", key, e);
            throw new CacheException("Failed to set expiration in Redis", e);
        }
    }

    @Override
    public Set<String> keys(String pattern) {
        if (pattern == null) {
            throw new CacheException("Pattern cannot be null for keys operation");
        }

        String redisPattern = formatKey(pattern);

        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys(redisPattern);
            // Enlever le préfixe des clés retournées
            return keys.stream()
                    .map(this::stripKeyPrefix)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Error getting keys from Redis with pattern: {}", pattern, e);
            throw new CacheException("Failed to get keys from Redis", e);
        }
    }

    @Override
    public boolean ping() {
        try (Jedis jedis = jedisPool.getResource()) {
            boolean success = "PONG".equalsIgnoreCase(jedis.ping());
            log.debug("Redis ping result: {}", success ? "PONG" : "FAILED");
            return success;
        } catch (Exception e) {
            log.error("Error pinging Redis", e);
            return false;
        }
    }

    @Override
    public void hset(String key, String field, Object value) {
        if (key == null || field == null) {
            throw new CacheException("Key and field cannot be null for hset operation");
        }

        String redisKey = formatKey(key);

        try (Jedis jedis = jedisPool.getResource()) {
            byte[] serialized = serialize(value);
            jedis.hset(redisKey.getBytes(), field.getBytes(), serialized);
            log.debug("Stored hash value in Redis for key: {}, field: {}", key, field);
        } catch (Exception e) {
            log.error("Error storing hash value in Redis for key: {}, field: {}", key, field, e);
            throw new CacheException("Failed to store hash value in Redis", e);
        }
    }

    @Override
    public Object hget(String key, String field) {
        if (key == null || field == null) {
            return null;
        }

        String redisKey = formatKey(key);

        try (Jedis jedis = jedisPool.getResource()) {
            byte[] data = jedis.hget(redisKey.getBytes(), field.getBytes());

            if (data == null) {
                if (config.isEnableStatistics()) {
                    statistics.get().recordMiss();
                }
                log.debug("Cache miss for hash key: {}, field: {}", key, field);
                return null;
            }

            if (config.isEnableStatistics()) {
                statistics.get().recordHit();
            }

            Object value = deserialize(data);
            log.debug("Cache hit for hash key: {}, field: {}", key, field);
            return value;
        } catch (Exception e) {
            log.error("Error retrieving hash value from Redis for key: {}, field: {}", key, field, e);
            throw new CacheException("Failed to retrieve hash value from Redis", e);
        }
    }

    @Override
    public Map<String, Object> hgetAll(String key) {
        if (key == null) {
            return Collections.emptyMap();
        }

        String redisKey = formatKey(key);

        try (Jedis jedis = jedisPool.getResource()) {
            Map<byte[], byte[]> data = jedis.hgetAll(redisKey.getBytes());

            if (data.isEmpty()) {
                log.debug("Empty hash or non-existent key: {}", key);
                return Collections.emptyMap();
            }

            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<byte[], byte[]> entry : data.entrySet()) {
                String field = new String(entry.getKey());
                Object value = deserialize(entry.getValue());
                result.put(field, value);
            }

            log.debug("Retrieved all fields for hash key: {}, count: {}", key, result.size());
            return result;
        } catch (Exception e) {
            log.error("Error retrieving all hash values from Redis for key: {}", key, e);
            throw new CacheException("Failed to retrieve all hash values from Redis", e);
        }
    }

    private String formatKey(String key) {
        return keyPrefix + key;
    }

    private String stripKeyPrefix(String key) {
        if (key.startsWith(keyPrefix)) {
            return key.substring(keyPrefix.length());
        }
        return key;
    }

    private byte[] serialize(Object obj) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error serializing object", e);
            throw new CacheException("Failed to serialize object", e);
        }
    }

    private Object deserialize(byte[] data) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            log.error("Error deserializing object", e);
            throw new CacheException("Failed to deserialize object", e);
        }
    }
}

