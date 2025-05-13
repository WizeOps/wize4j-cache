package net.wizeops.wize4j.cache.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Cacheable {
    String value();
    String key() default "";
    long ttlSeconds() default 3600;
}



