package org.jboss.netty.handler.codec.serialization;

class ClassLoaderClassResolver implements ClassResolver {

    private final ClassLoader classLoader;

    ClassLoaderClassResolver(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public Class<?> resolve(String className) throws ClassNotFoundException {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            return Class.forName(className, false, classLoader);
        }
    }

}
