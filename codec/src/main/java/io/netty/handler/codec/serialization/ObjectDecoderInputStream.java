/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.serialization;

import io.netty.util.internal.ObjectUtil;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.StreamCorruptedException;

/**
 * An {@link ObjectInput} which is interoperable with {@link ObjectEncoder}
 * and {@link ObjectEncoderOutputStream}.
 * <p>
 * <strong>Security:</strong> serialization can be a security liability,
 * and should not be used without defining a list of classes that are
 * allowed to be desirialized. Such a list can be specified with the
 * <tt>jdk.serialFilter</tt> system property, for instance.
 * See the <a href="https://docs.oracle.com/en/java/javase/17/core/serialization-filtering1.html">
 * serialization filtering</a> article for more information.
 *
 * @deprecated This class has been deprecated with no replacement,
 * because serialization can be a security liability
 */
@Deprecated
public class ObjectDecoderInputStream extends InputStream implements
        ObjectInput {

    private final DataInputStream in;
    private final int maxObjectSize;
    private final ClassResolver classResolver;

    /**
     * Creates a new {@link ObjectInput}.
     *
     * @param in
     *        the {@link InputStream} where the serialized form will be
     *        read from
     */
    public ObjectDecoderInputStream(InputStream in) {
        this(in, null);
    }

    /**
     * Creates a new {@link ObjectInput}.
     *
     * @param in
     *        the {@link InputStream} where the serialized form will be
     *        read from
     * @param classLoader
     *        the {@link ClassLoader} which will load the class of the
     *        serialized object
     */
    public ObjectDecoderInputStream(InputStream in, ClassLoader classLoader) {
        this(in, classLoader, 1048576);
    }

    /**
     * Creates a new {@link ObjectInput}.
     *
     * @param in
     *        the {@link InputStream} where the serialized form will be
     *        read from
     * @param maxObjectSize
     *        the maximum byte length of the serialized object.  if the length
     *        of the received object is greater than this value,
     *        a {@link StreamCorruptedException} will be raised.
     */
    public ObjectDecoderInputStream(InputStream in, int maxObjectSize) {
        this(in, null, maxObjectSize);
    }

    /**
     * Creates a new {@link ObjectInput}.
     *
     * @param in
     *        the {@link InputStream} where the serialized form will be
     *        read from
     * @param classLoader
     *        the {@link ClassLoader} which will load the class of the
     *        serialized object
     * @param maxObjectSize
     *        the maximum byte length of the serialized object.  if the length
     *        of the received object is greater than this value,
     *        a {@link StreamCorruptedException} will be raised.
     */
    public ObjectDecoderInputStream(InputStream in, ClassLoader classLoader, int maxObjectSize) {
        ObjectUtil.checkNotNull(in, "in");
        ObjectUtil.checkPositive(maxObjectSize, "maxObjectSize");

        if (in instanceof DataInputStream) {
            this.in = (DataInputStream) in;
        } else {
            this.in = new DataInputStream(in);
        }
        classResolver = ClassResolvers.weakCachingResolver(classLoader);
        this.maxObjectSize = maxObjectSize;
    }

    @Override
    public Object readObject() throws ClassNotFoundException, IOException {
        int dataLen = readInt();
        if (dataLen <= 0) {
            throw new StreamCorruptedException("invalid data length: " + dataLen);
        }
        if (dataLen > maxObjectSize) {
            throw new StreamCorruptedException(
                    "data length too big: " + dataLen + " (max: " + maxObjectSize + ')');
        }

        return new CompactObjectInputStream(in, classResolver).readObject();
    }

    @Override
    public int available() throws IOException {
        return in.available();
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    // Suppress a warning since the class is not thread-safe
    @Override
    public void mark(int readlimit) {   // lgtm[java/non-sync-override]
        in.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        return in.markSupported();
    }

    // Suppress a warning since the class is not thread-safe
    @Override
    public int read() throws IOException {  // lgtm[java/non-sync-override]
        return in.read();
    }

    @Override
    public final int read(byte[] b, int off, int len) throws IOException {
        return in.read(b, off, len);
    }

    @Override
    public final int read(byte[] b) throws IOException {
        return in.read(b);
    }

    @Override
    public final boolean readBoolean() throws IOException {
        return in.readBoolean();
    }

    @Override
    public final byte readByte() throws IOException {
        return in.readByte();
    }

    @Override
    public final char readChar() throws IOException {
        return in.readChar();
    }

    @Override
    public final double readDouble() throws IOException {
        return in.readDouble();
    }

    @Override
    public final float readFloat() throws IOException {
        return in.readFloat();
    }

    @Override
    public final void readFully(byte[] b, int off, int len) throws IOException {
        in.readFully(b, off, len);
    }

    @Override
    public final void readFully(byte[] b) throws IOException {
        in.readFully(b);
    }

    @Override
    public final int readInt() throws IOException {
        return in.readInt();
    }

    /**
     * @deprecated Use {@link BufferedReader#readLine()} instead.
     */
    @Override
    @Deprecated
    public final String readLine() throws IOException {
        return in.readLine();
    }

    @Override
    public final long readLong() throws IOException {
        return in.readLong();
    }

    @Override
    public final short readShort() throws IOException {
        return in.readShort();
    }

    @Override
    public final int readUnsignedByte() throws IOException {
        return in.readUnsignedByte();
    }

    @Override
    public final int readUnsignedShort() throws IOException {
        return in.readUnsignedShort();
    }

    @Override
    public final String readUTF() throws IOException {
        return in.readUTF();
    }

    @Override
    public void reset() throws IOException {
        in.reset();
    }

    @Override
    public long skip(long n) throws IOException {
        return in.skip(n);
    }

    @Override
    public final int skipBytes(int n) throws IOException {
        return in.skipBytes(n);
    }
}
