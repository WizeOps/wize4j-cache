package net.wizeops.wize4j.cache.utils;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.util.Arrays;
import java.util.stream.Collectors;

public class KeyGenerator {

    public static String generateKey(ProceedingJoinPoint joinPoint, String keyPattern) {
        if (keyPattern == null || keyPattern.isEmpty()) {
            return generateDefaultKey(joinPoint);
        }
        return resolveKeyPattern(joinPoint, keyPattern);
    }

    static String generateDefaultKey(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        String args = Arrays.stream(joinPoint.getArgs())
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return String.format("%s(%s)", methodName, args);
    }

    private static String resolveKeyPattern(ProceedingJoinPoint joinPoint, String keyPattern) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        String result = keyPattern
                .replace("#method", methodName);

        Object[] args = joinPoint.getArgs();
        String[] parameterNames = signature.getParameterNames();

        for (int i = 0; i < parameterNames.length; i++) {
            String paramName = parameterNames[i];
            String value = args[i] != null ? args[i].toString() : "null";
            result = result.replace("#" + paramName, value);
        }

        return result;
    }


}

