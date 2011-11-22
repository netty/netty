package org.jboss.netty.handler.codec.serialization;

class ClassloaderClassResolver implements ClassResolver {

    private final ClassLoader classLoader;

    ClassloaderClassResolver(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public Class<?> resolve(String className) throws ClassNotFoundException {
        return classLoader.loadClass(className);
    }

}
