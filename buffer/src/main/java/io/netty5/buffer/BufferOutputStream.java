/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer;

import io.netty5.buffer.api.Buffer;
import io.netty5.util.CharsetUtil;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static java.util.Objects.requireNonNull;

/**
 * An {@link OutputStream} which writes data to a {@link Buffer}.
 * <p>
 * A write operation against this stream will occur at the {@code writerOffset}
 * of its underlying buffer and the {@code writerOffset} will increase during
 * the write operation.
 * <p>
 * This stream implements {@link DataOutput} for your convenience.
 * The endianness of the stream is always big endian.
 *
 * @see BufferInputStream
 */
public final class BufferOutputStream extends OutputStream implements DataOutput {
    private final Buffer buffer;
    private final int startIndex;
    private DataOutputStream utf8out; // lazily-instantiated
    private boolean closed;

    /**
     * Creates a new stream which writes data to the specified {@code buffer}.
     */
    public BufferOutputStream(Buffer buffer) {
        this.buffer = requireNonNull(buffer, "buffer");
        startIndex = buffer.writerOffset();
    }

    /**
     * Returns the number of written bytes by this stream so far.
     */
    public int writtenBytes() {
        return buffer.writerOffset() - startIndex;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        prepareWrite(len);
        if (len > 0) {
            buffer.writeBytes(b, off, len);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        prepareWrite(b.length);
        if (b.length > 0) {
            buffer.writeBytes(b);
        }
    }

    @Override
    public void write(int b) throws IOException {
        prepareWrite(Integer.BYTES);
        buffer.writeByte((byte) b);
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        prepareWrite(1);
        buffer.writeByte((byte) (v ? 1 : 0));
    }

    @Override
    public void writeByte(int v) throws IOException {
        prepareWrite(1);
        buffer.writeByte((byte) v);
    }

    @Override
    public void writeBytes(String s) throws IOException {
        prepareWrite(s.length()); // US ASCII has no multibyte values, so this works.
        buffer.writeCharSequence(s, CharsetUtil.US_ASCII);
    }

    @Override
    public void writeChar(int v) throws IOException {
        prepareWrite(Character.BYTES);
        buffer.writeChar((char) v);
    }

    @Override
    public void writeChars(String s) throws IOException {
        int len = s.length();
        prepareWrite(len * Character.BYTES);
        for (int i = 0 ; i < len ; i ++) {
            buffer.writeChar(s.charAt(i));
        }
    }

    @Override
    public void writeDouble(double v) throws IOException {
        prepareWrite(Double.BYTES);
        buffer.writeDouble(v);
    }

    @Override
    public void writeFloat(float v) throws IOException {
        prepareWrite(Float.BYTES);
        buffer.writeFloat(v);
    }

    @Override
    public void writeInt(int v) throws IOException {
        prepareWrite(Integer.BYTES);
        buffer.writeInt(v);
    }

    @Override
    public void writeLong(long v) throws IOException {
        prepareWrite(Long.BYTES);
        buffer.writeLong(v);
    }

    @Override
    public void writeShort(int v) throws IOException {
        prepareWrite(Short.BYTES);
        buffer.writeShort((short) v);
    }

    @Override
    public void writeUTF(String s) throws IOException {
        if (closed) {
            throw new IOException("The stream is closed");
        }
        DataOutputStream out = utf8out;
        if (out == null) {
            // Suppress a warning since the stream is closed in the close() method
            utf8out = out = new DataOutputStream(this); // lgtm[java/output-resource-leak]
        }
        out.writeUTF(s);
    }

    /**
     * Returns the buffer where this stream is writing data.
     */
    public Buffer buffer() {
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
        }
    }

    private void prepareWrite(int len) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        if (len > 0) {
            buffer.ensureWritable(len, buffer.capacity() >> 2, true);
        }
    }
}
