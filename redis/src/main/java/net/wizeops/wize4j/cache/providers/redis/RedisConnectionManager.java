package net.wizeops.wize4j.cache.providers.redis;

import lombok.extern.slf4j.Slf4j;
import net.wizeops.wize4j.cache.config.CacheConfiguration;
import net.wizeops.wize4j.cache.exceptions.CacheException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.function.Function;

@Slf4j
public class RedisConnectionManager implements AutoCloseable {
    private final JedisPool jedisPool;

    public RedisConnectionManager(CacheConfiguration config) {
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

    public <T> T execute(Function<Jedis, T> action) {
        try (Jedis jedis = jedisPool.getResource()) {
            return action.apply(jedis);
        } catch (Exception e) {
            log.error("Error executing Redis operation", e);
            throw new CacheException("Failed to execute Redis operation", e);
        }
    }

    public void execute(java.util.function.Consumer<Jedis> action) {
        try (Jedis jedis = jedisPool.getResource()) {
            action.accept(jedis);
        } catch (Exception e) {
            log.error("Error executing Redis operation", e);
            throw new CacheException("Failed to execute Redis operation", e);
        }
    }

    @Override
    public void close() {
        jedisPool.close();
        log.info("Redis connection manager closed");
    }
}
