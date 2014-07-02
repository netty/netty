/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.serialization;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.StreamCorruptedException;

class CompactObjectInputStream extends ObjectInputStream {

    private final ClassResolver classResolver;

    CompactObjectInputStream(InputStream in, ClassResolver classResolver) throws IOException {
        super(in);
        this.classResolver = classResolver;
    }

    @Override
    protected void readStreamHeader() throws IOException {
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
        case CompactObjectOutputStream.TYPE_FAT_DESCRIPTOR:
            return super.readClassDescriptor();
        case CompactObjectOutputStream.TYPE_THIN_DESCRIPTOR:
            String className = readUTF();
            Class<?> clazz = classResolver.resolve(className);
            return ObjectStreamClass.lookupAny(clazz);
        default:
            throw new StreamCorruptedException(
                    "Unexpected class descriptor type: " + type);
        }
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        Class<?> clazz;
        try {
            clazz = classResolver.resolve(desc.getName());
        } catch (ClassNotFoundException ignored) {
            clazz = super.resolveClass(desc);
        }

        return clazz;
    }

}
