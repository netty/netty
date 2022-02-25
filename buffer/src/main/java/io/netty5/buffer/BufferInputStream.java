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
import io.netty5.buffer.api.Send;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.UnstableApi;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import static java.util.Objects.requireNonNull;

/**
 * An {@link InputStream} which reads data from a {@link Buffer}.
 * <p>
 * A read operation against this stream will occur at the {@code readerOffset}
 * of its underlying buffer and the {@code readerOffset} will increase during
 * the read operation.  Please note that it only reads up to the number of
 * readable bytes determined at the moment of construction.  Therefore,
 * updating {@link Buffer#writerOffset()} will not affect the return
 * value of {@link #available()}.
 * <p>
 * This stream implements {@link DataInput} for your convenience.
 * The endianness of the stream is always big endian.
 *
 * @see BufferOutputStream
 */
public final class BufferInputStream extends InputStream implements DataInput {
    private final Buffer buffer;
    private final int startIndex;
    private final int endIndex;
    private boolean closed;
    private int markReaderOffset;

    /**
     * Creates a new stream which reads data from the specified {@code buffer} starting at the current
     * {@code readerOffset} and ending at the current {@code writerOffset}.
     * <p>
     * When this {@link BufferInputStream} is {@linkplain #close() closed, then the sent buffer will also be closed.
     *
     * @param buffer The buffer which provides the content for this {@link InputStream}.
     */
    public BufferInputStream(Send<Buffer> buffer) {
        this.buffer = requireNonNull(buffer, "buffer").receive();
        int readableBytes = this.buffer.readableBytes();
        startIndex = this.buffer.readerOffset();
        endIndex = startIndex + readableBytes;
        markReaderOffset = startIndex;
    }

    /**
     * Returns the number of read bytes by this stream so far.
     */
    public int readBytes() {
        return buffer.readerOffset() - startIndex;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            // The Closable interface says "If the stream is already closed then invoking this method has no effect."
            return;
        }
        try (buffer) {
            closed = true;
            super.close();
        }
    }

    @Override
    public int available() throws IOException {
        return Math.max(0, endIndex - buffer.readerOffset());
    }

    // Suppress a warning since the class is not thread-safe
    @Override
    public void mark(int readlimit) { // lgtm[java/non-sync-override]
        markReaderOffset = buffer.readerOffset();
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public int read() throws IOException {
        checkOpen();
        int available = available();
        if (available == 0) {
            return -1;
        }
        return buffer.readByte() & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        checkOpen();
        int available = available();
        if (available == 0) {
            return -1;
        }

        len = Math.min(available, len);
        buffer.readBytes(b, off, len);
        return len;
    }

    // Suppress a warning since the class is not thread-safe
    @Override
    public void reset() throws IOException { // lgtm[java/non-sync-override]
        buffer.readerOffset(markReaderOffset);
    }

    @Override
    public long skip(long n) throws IOException {
        return skipBytes((int) Math.min(Integer.MAX_VALUE, n));
    }

    @Override
    public boolean readBoolean() throws IOException {
        checkAvailable(1);
        return read() != 0;
    }

    @Override
    public byte readByte() throws IOException {
        int available = available();
        if (available == 0) {
            throw new EOFException();
        }
        return buffer.readByte();
    }

    @Override
    public char readChar() throws IOException {
        return (char) readShort();
    }

    @Override
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        checkAvailable(len);
        buffer.readBytes(b, off, len);
    }

    @Override
    public int readInt() throws IOException {
        checkAvailable(4);
        return buffer.readInt();
    }

    private StringBuilder lineBuf;

    @Override
    public String readLine() throws IOException {
        int available = available();
        if (available == 0) {
            return null;
        }

        if (lineBuf != null) {
            lineBuf.setLength(0);
        }

        loop: do {
            int c = buffer.readUnsignedByte();
            --available;
            switch (c) {
                case '\n':
                    break loop;

                case '\r':
                    if (available > 0 && (char) buffer.getUnsignedByte(buffer.readerOffset()) == '\n') {
                        buffer.skipReadable(1);
                    }
                    break loop;

                default:
                    if (lineBuf == null) {
                        lineBuf = new StringBuilder();
                    }
                    lineBuf.append((char) c);
            }
        } while (available > 0);

        return lineBuf != null && lineBuf.length() > 0 ? lineBuf.toString() : StringUtil.EMPTY_STRING;
    }

    @Override
    public long readLong() throws IOException {
        checkAvailable(8);
        return buffer.readLong();
    }

    @Override
    public short readShort() throws IOException {
        checkAvailable(2);
        return buffer.readShort();
    }

    @Override
    public String readUTF() throws IOException {
        return DataInputStream.readUTF(this);
    }

    @Override
    public int readUnsignedByte() throws IOException {
        return readByte() & 0xff;
    }

    @Override
    public int readUnsignedShort() throws IOException {
        return readShort() & 0xffff;
    }

    @Override
    public int skipBytes(int n) throws IOException {
        int nBytes = Math.min(available(), n);
        buffer.skipReadable(nBytes);
        return nBytes;
    }

    /**
     * Expose the internally owned buffer.
     * Use only for testing purpose.
     *
     * @return The internal buffer instance.
     */
    Buffer buffer() {
        return buffer;
    }

    private void checkOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
    }

    private void checkAvailable(int fieldSize) throws IOException {
        checkOpen();
        if (fieldSize < 0) {
            throw new IndexOutOfBoundsException("fieldSize cannot be a negative number");
        }
        if (fieldSize > available()) {
            throw new EOFException("fieldSize is too long! Length is " + fieldSize
                    + ", but maximum is " + available());
        }
    }
}
