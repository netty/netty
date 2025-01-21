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
package io.netty.buffer;

import io.netty.util.CharsetUtil;
import io.netty.util.internal.ObjectUtil;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An {@link OutputStream} which writes data to a {@link ByteBuf}.
 * <p>
 * A write operation against this stream will occur at the {@code writerIndex}
 * of its underlying buffer and the {@code writerIndex} will increase during
 * the write operation.
 * <p>
 * This stream implements {@link DataOutput} for your convenience.
 * The endianness of the stream is not always big endian but depends on
 * the endianness of the underlying buffer.
 *
 * @see ByteBufInputStream
 */
public class ByteBufOutputStream extends OutputStream implements DataOutput {

    private final ByteBuf buffer;
    private final int startIndex;
    private DataOutputStream utf8out; // lazily-instantiated
    private boolean closed;
    private final boolean releaseOnClose;

    /**
     * Creates a new stream which writes data to the specified {@code buffer}.
     */
    public ByteBufOutputStream(ByteBuf buffer) {
        this(buffer, false);
    }

    /**
     * Creates a new stream which writes data to the specified {@code buffer}.
     *
     * @param buffer Writes data to the buffer for this {@link OutputStream}.
     * @param releaseOnClose {@code true} means that when {@link #close()} is called then {@link ByteBuf#release()} will
     *                       be called on {@code buffer}.
     */
    public ByteBufOutputStream(ByteBuf buffer, boolean releaseOnClose) {
        this.releaseOnClose = releaseOnClose;
        this.buffer = ObjectUtil.checkNotNull(buffer, "buffer");
        startIndex = buffer.writerIndex();
    }

    /**
     * Returns the number of written bytes by this stream so far.
     */
    public int writtenBytes() {
        return buffer.writerIndex() - startIndex;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return;
        }

        buffer.writeBytes(b, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException {
        buffer.writeBytes(b);
    }

    @Override
    public void write(int b) throws IOException {
        buffer.writeByte(b);
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        buffer.writeBoolean(v);
    }

    @Override
    public void writeByte(int v) throws IOException {
        buffer.writeByte(v);
    }

    @Override
    public void writeBytes(String s) throws IOException {
        buffer.writeCharSequence(s, CharsetUtil.US_ASCII);
    }

    @Override
    public void writeChar(int v) throws IOException {
        buffer.writeChar(v);
    }

    @Override
    public void writeChars(String s) throws IOException {
        int len = s.length();
        for (int i = 0; i < len; i++) {
            buffer.writeChar(s.charAt(i));
        }
    }

    @Override
    public void writeDouble(double v) throws IOException {
        buffer.writeDouble(v);
    }

    @Override
    public void writeFloat(float v) throws IOException {
        buffer.writeFloat(v);
    }

    @Override
    public void writeInt(int v) throws IOException {
        buffer.writeInt(v);
    }

    @Override
    public void writeLong(long v) throws IOException {
        buffer.writeLong(v);
    }

    @Override
    public void writeShort(int v) throws IOException {
        buffer.writeShort((short) v);
    }

    @Override
    public void writeUTF(String s) throws IOException {
        DataOutputStream out = utf8out;
        if (out == null) {
            if (closed) {
                throw new IOException("The stream is closed");
            }
            // Suppress a warning since the stream is closed in the close() method
            utf8out = out = new DataOutputStream(this);
        }
        out.writeUTF(s);
    }

    /**
     * Returns the buffer where this stream is writing data.
     */
    public ByteBuf buffer() {
        return buffer;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        try {
            super.close();
        } finally {
            if (utf8out != null) {
                utf8out.close();
            }
            if (releaseOnClose) {
                buffer.release();
            }
        }
    }
}
