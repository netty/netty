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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * An {@link ObjectOutput} which is interoperable with {@link ObjectDecoder}
 * and {@link ObjectDecoderInputStream}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class ObjectEncoderOutputStream extends OutputStream implements
        ObjectOutput {

    private final DataOutputStream out;
    private final int estimatedLength;

    /**
     * Creates a new {@link ObjectOutput} with the estimated length of 512
     * bytes.
     *
     * @param out
     *        the {@link OutputStream} where the serialized form will be
     *        written out
     */
    public ObjectEncoderOutputStream(OutputStream out) {
        this(out, 512);
    }

    /**
     * Creates a new {@link ObjectOutput}.
     *
     * @param out
     *        the {@link OutputStream} where the serialized form will be
     *        written out
     *
     * @param estimatedLength
     *        the estimated byte length of the serialized form of an object.
     *        If the length of the serialized form exceeds this value, the
     *        internal buffer will be expanded automatically at the cost of
     *        memory bandwidth.  If this value is too big, it will also waste
     *        memory bandwidth.  To avoid unnecessary memory copy or allocation
     *        cost, please specify the properly estimated value.
     */
    public ObjectEncoderOutputStream(OutputStream out, int estimatedLength) {
        if (out == null) {
            throw new NullPointerException("out");
        }
        if (estimatedLength < 0) {
            throw new IllegalArgumentException("estimatedLength: " + estimatedLength);
        }

        if (out instanceof DataOutputStream) {
            this.out = (DataOutputStream) out;
        } else {
            this.out = new DataOutputStream(out);
        }
        this.estimatedLength = estimatedLength;
    }

    public void writeObject(Object obj) throws IOException {
        ChannelBufferOutputStream bout = new ChannelBufferOutputStream(ChannelBuffers.dynamicBuffer(estimatedLength));
        ObjectOutputStream oout = new CompactObjectOutputStream(bout);
        oout.writeObject(obj);
        oout.flush();
        oout.close();

        ChannelBuffer buffer = bout.buffer();
        int objectSize = buffer.readableBytes();
        writeInt(objectSize);
        buffer.getBytes(0, this, objectSize);
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    public final int size() {
        return out.size();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException {
        out.write(b);
    }

    public final void writeBoolean(boolean v) throws IOException {
        out.writeBoolean(v);
    }

    public final void writeByte(int v) throws IOException {
        out.writeByte(v);
    }

    public final void writeBytes(String s) throws IOException {
        out.writeBytes(s);
    }

    public final void writeChar(int v) throws IOException {
        out.writeChar(v);
    }

    public final void writeChars(String s) throws IOException {
        out.writeChars(s);
    }

    public final void writeDouble(double v) throws IOException {
        out.writeDouble(v);
    }

    public final void writeFloat(float v) throws IOException {
        out.writeFloat(v);
    }

    public final void writeInt(int v) throws IOException {
        out.writeInt(v);
    }

    public final void writeLong(long v) throws IOException {
        out.writeLong(v);
    }

    public final void writeShort(int v) throws IOException {
        out.writeShort(v);
    }

    public final void writeUTF(String str) throws IOException {
        out.writeUTF(str);
    }
}
