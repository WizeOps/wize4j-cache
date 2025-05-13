package net.wizeops.wize4j.cache.compression;

import net.wizeops.wize4j.cache.utils.CacheUtil;

public class DefaultCompressionStrategy implements CompressionStrategy {
    @Override
    public byte[] compress(Object value) {
        return CacheUtil.compress(value);
    }

    @Override
    public Object decompress(byte[] compressed) {
        return CacheUtil.decompress(compressed);
    }
}

