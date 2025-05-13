package net.wizeops.wize4j.cache.compression;

public interface CompressionStrategy {
    byte[] compress(Object value);
    Object decompress(byte[] compressed);
}

