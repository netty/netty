/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.codec.serialization;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.StreamCorruptedException;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
class CompactObjectInputStream extends ObjectInputStream {

    private final ClassLoader classLoader;

    CompactObjectInputStream(InputStream in) throws IOException {
        this(in, null);
    }

    CompactObjectInputStream(InputStream in, ClassLoader classLoader) throws IOException {
        super(in);
        this.classLoader = classLoader;
    }

    @Override
    protected void readStreamHeader() throws IOException,
            StreamCorruptedException {
        int version = readByte() & 0xFF;
        if (version != STREAM_VERSION) {
            throw new StreamCorruptedException(
                    "Unsupported version: " + version);
        }
    }

    @Override
    protected ObjectStreamClass readClassDescriptor()
            throws IOException, ClassNotFoundException {
        int type = read();
        if (type < 0) {
            throw new EOFException();
        }
        switch (type) {
        case CompactObjectOutputStream.TYPE_PRIMITIVE:
            return super.readClassDescriptor();
        case CompactObjectOutputStream.TYPE_NON_PRIMITIVE:
            String className = readUTF();
            Class<?> clazz = loadClass(className);
            return ObjectStreamClass.lookup(clazz);
        default:
            throw new StreamCorruptedException(
                    "Unexpected class descriptor type: " + type);
        }
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        String className = desc.getName();
        try {
            return loadClass(className);
        } catch (ClassNotFoundException ex) {
            return super.resolveClass(desc);
        }
    }

    private Class<?> loadClass(String className) throws ClassNotFoundException {
        Class<?> clazz;
        ClassLoader classLoader = this.classLoader;
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }

        if (classLoader != null) {
            clazz = classLoader.loadClass(className);
        } else {
            clazz = Class.forName(className);
        }
        return clazz;
    }
}
