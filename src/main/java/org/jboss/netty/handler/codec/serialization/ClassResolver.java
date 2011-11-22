package org.jboss.netty.handler.codec.serialization;

interface ClassResolver {

    Class<?> resolve(String className) throws ClassNotFoundException;

}
