package org.jboss.netty.handler.codec.serialization;

class ClassloaderClassResolver implements ClassResolver {

    private final ClassLoader classLoader;

    ClassloaderClassResolver(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    
    /*
     * (non-Javadoc)
     * @see org.jboss.netty.handler.codec.serialization.ClassResolver#resolve(java.lang.String)
     */
    public Class<?> resolve(String className) throws ClassNotFoundException {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            return Class.forName(className, false, classLoader);
        }
    }

}
