package org.jboss.netty.handler.codec.serialization;

import java.util.HashMap;

public class ClassResolvers {
    
    public static ClassResolver cachingResolver(ClassLoader classLoader) {
        if (classLoader == null) {
            classLoader = defaultClassLoader();
        }

        return new CachingClassResolver(new ClassloaderClassResolver(classLoader), new HashMap<String, Class<?>>());
    }
    
    static ClassLoader defaultClassLoader() {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            return contextClassLoader;
        }

        return CompactObjectInputStream.class.getClassLoader();
    }

}
