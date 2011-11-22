package org.jboss.netty.handler.codec.serialization;

import java.util.Map;

class CachingClassResolver implements ClassResolver {

    private final Map<String, Class<?>> classCache;
    private final ClassResolver delegate;

    CachingClassResolver(ClassResolver delegate, Map<String, Class<?>> classCache) {
        this.delegate = delegate;
        this.classCache = classCache;
    }

    @Override
    public Class<?> resolve(String className) throws ClassNotFoundException {
        // Query the cache first.
        Class<?> clazz;
        clazz = classCache.get(className);
        if (clazz != null) {
            return clazz;
        }

        // And then try to load.
        clazz = delegate.resolve(className);

        classCache.put(className, clazz);
        return clazz;
    }

}
