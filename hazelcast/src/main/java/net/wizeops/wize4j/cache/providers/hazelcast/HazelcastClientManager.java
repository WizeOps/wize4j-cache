package net.wizeops.wize4j.cache.providers.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.core.HazelcastInstance;
import lombok.extern.slf4j.Slf4j;
import net.wizeops.wize4j.cache.config.CacheConfiguration;
import net.wizeops.wize4j.cache.exceptions.CacheException;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class HazelcastClientManager implements AutoCloseable {
    private static final AtomicReference<HazelcastClientManager> INSTANCE = new AtomicReference<>();

    private final HazelcastInstance hazelcastInstance;

    private HazelcastClientManager(CacheConfiguration config) {
        this.hazelcastInstance = createHazelcastInstance(config);
    }

    public static synchronized HazelcastClientManager getInstance(CacheConfiguration config) {
        if (INSTANCE.get() == null) {
            INSTANCE.set(new HazelcastClientManager(config));
        }
        return INSTANCE.get();
    }

    public HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
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

    @Override
    public void close() {
        if (hazelcastInstance != null) {
            try {
                hazelcastInstance.shutdown();
                log.info("Hazelcast client instance shut down");
            } catch (Exception e) {
                log.error("Error shutting down Hazelcast client instance", e);
            }
        }
        INSTANCE.set(null);
    }
}