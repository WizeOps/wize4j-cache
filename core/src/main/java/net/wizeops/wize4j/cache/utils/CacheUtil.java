package net.wizeops.wize4j.cache.utils;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


@Slf4j
public class CacheUtil {

    public static String generateKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        return KeyGenerator.generateKey(joinPoint, keyExpression);
    }


    private static String evaluateKeyExpression(ProceedingJoinPoint joinPoint, String keyExpression) {
        try {
            StringBuilder key = new StringBuilder();
            key.append(joinPoint.getSignature().getName());

            Object[] args = joinPoint.getArgs();
            if (args != null) {
                for (Object arg : args) {
                    key.append(":").append(arg != null ? arg.toString() : "null");
                }
            }

            return key.toString();
        } catch (Exception e) {
            log.error("Error generating key: {}", keyExpression, e);
            return KeyGenerator.generateDefaultKey(joinPoint);
        }
    }

    public static byte[] compress(Object value) {
        if (value == null) return null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzos = new GZIPOutputStream(baos);
             ObjectOutputStream oos = new ObjectOutputStream(gzos)) {

            oos.writeObject(value);
            oos.flush();
            gzos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error compressing object", e);
            throw new RuntimeException("Failed to compress object", e);
        }
    }

    public static Object decompress(byte[] compressed) {
        if (compressed == null) return null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             GZIPInputStream gzis = new GZIPInputStream(bais);
             ObjectInputStream ois = new ObjectInputStream(gzis)) {

            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            log.error("Error decompressing object", e);
            throw new RuntimeException("Failed to decompress object", e);
        }
    }

    public static long estimateObjectSize(Object obj) {
        if (obj == null) return 0;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(obj);
            oos.flush();
            return baos.size();
        } catch (IOException e) {
            log.warn("Could not estimate object size accurately, using default estimation", e);
            return estimateSizeByClass(obj);
        }
    }

    private static long estimateSizeByClass(Object obj) {
        if (obj instanceof String) {
            return 24 + ((String) obj).length() * 2L; // 24 bytes overhead + 2 bytes per char
        } else if (obj instanceof Number) {
            return 16; // Taille approximative pour les types numériques
        } else if (obj instanceof Boolean) {
            return 1;
        } else if (obj.getClass().isArray()) {
            return estimateArraySize(obj);
        }
        return 32; // Taille par défaut pour les autres objets
    }

    private static long estimateArraySize(Object array) {
        int length = java.lang.reflect.Array.getLength(array);
        int elementSize = 8; // Taille par défaut pour chaque élément
        return 16 + ((long) length * elementSize); // 16 bytes overhead + taille des éléments
    }
}



