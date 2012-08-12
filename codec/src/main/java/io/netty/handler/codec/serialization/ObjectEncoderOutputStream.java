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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * An {@link ObjectOutput} which is interoperable with {@link ObjectDecoder}
 * and {@link ObjectDecoderInputStream}.
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

    @Override
    public void writeObject(Object obj) throws IOException {
        ByteBufOutputStream bout = new ByteBufOutputStream(Unpooled.buffer(estimatedLength));
        ObjectOutputStream oout = new CompactObjectOutputStream(bout);
        oout.writeObject(obj);
        oout.flush();
        oout.close();

        ByteBuf buffer = bout.buffer();
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

    @Override
    public final void writeBoolean(boolean v) throws IOException {
        out.writeBoolean(v);
    }

    @Override
    public final void writeByte(int v) throws IOException {
        out.writeByte(v);
    }

    @Override
    public final void writeBytes(String s) throws IOException {
        out.writeBytes(s);
    }

    @Override
    public final void writeChar(int v) throws IOException {
        out.writeChar(v);
    }

    @Override
    public final void writeChars(String s) throws IOException {
        out.writeChars(s);
    }

    @Override
    public final void writeDouble(double v) throws IOException {
        out.writeDouble(v);
    }

    @Override
    public final void writeFloat(float v) throws IOException {
        out.writeFloat(v);
    }

    @Override
    public final void writeInt(int v) throws IOException {
        out.writeInt(v);
    }

    @Override
    public final void writeLong(long v) throws IOException {
        out.writeLong(v);
    }

    @Override
    public final void writeShort(int v) throws IOException {
        out.writeShort(v);
    }

    @Override
    public final void writeUTF(String str) throws IOException {
        out.writeUTF(str);
    }
}
