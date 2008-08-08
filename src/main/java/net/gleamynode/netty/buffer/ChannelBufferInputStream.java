/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.buffer;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 *
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @see ChannelBufferOutputStream
 * @apiviz.uses net.gleamynode.netty.buffer.ChannelBuffer
 */
public class ChannelBufferInputStream extends InputStream implements DataInput {

    private final ChannelBuffer buffer;
    private final int endIndex;
    private int offset;
    private Integer mark;

    public ChannelBufferInputStream(ChannelBuffer buffer) {
        this(buffer, buffer.readerIndex(), buffer.readableBytes());
    }

    public ChannelBufferInputStream(ChannelBuffer buffer, int startIndex, int length) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        if (startIndex < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (length < 0 || startIndex + length > buffer.capacity()) {
            throw new IndexOutOfBoundsException();
        }

        this.buffer = buffer;
        offset = startIndex;
        endIndex = startIndex + length;
    }

    @Override
    public int available() throws IOException {
        return endIndex - offset;
    }

    @Override
    public void mark(int readlimit) {
        mark = offset;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public int read() throws IOException {
        if (offset >= endIndex) {
            return -1;
        }
        return buffer.getByte(offset ++) & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int available = available();
        if (available == 0) {
            return -1;
        }

        len = Math.min(available, len);
        buffer.getBytes(offset, b, off, len);
        offset += len;
        return len;
    }

    @Override
    public void reset() throws IOException {
        if (mark == null) {
            throw new IOException("mark() not called.");
        }
        offset = mark;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n > Integer.MAX_VALUE) {
            return skipBytes(Integer.MAX_VALUE);
        } else {
            return skipBytes((int) n);
        }
    }

    public boolean readBoolean() throws IOException {
        int b = read();
        if (b < 0) {
            throw new EOFException();
        }
        return b != 0;
    }

    public byte readByte() throws IOException {
        if (offset >= endIndex) {
            throw new EOFException();
        }
        return buffer.getByte(offset ++);
    }

    public char readChar() throws IOException {
        return (char) readShort();
    }

    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    public void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    public void readFully(byte[] b, int off, int len) throws IOException {
        checkIndex(len);
        buffer.getBytes(offset, b, off, len);
        offset += len;
    }

    public int readInt() throws IOException {
        checkIndex(4);
        int answer = buffer.getInt(offset);
        offset += 4;
        return answer;
    }

    private final StringBuilder lineBuf = new StringBuilder();

    public String readLine() throws IOException {
        lineBuf.setLength(0);
        for (;;) {
            int b = read();
            if (b < 0 || b == '\n') {
                break;
            }

            lineBuf.append((char) b);
        }

        while (lineBuf.charAt(lineBuf.length() - 1) == '\r') {
            lineBuf.setLength(lineBuf.length() - 1);
        }

        return lineBuf.toString();
    }

    public long readLong() throws IOException {
        checkIndex(8);
        long answer = buffer.getLong(offset);
        offset += 8;
        return answer;
    }

    public short readShort() throws IOException {
        checkIndex(2);
        short answer = buffer.getShort(offset);
        offset += 2;
        return answer;
    }

    public String readUTF() throws IOException {
        return DataInputStream.readUTF(this);
    }

    public int readUnsignedByte() throws IOException {
        return readByte() & 0xff;
    }

    public int readUnsignedShort() throws IOException {
        return readShort() & 0xffff;
    }

    public int skipBytes(int n) throws IOException {
        int nBytes = Math.min(available(), n);
        offset += nBytes;
        return nBytes;
    }

    private void checkIndex(int fieldSize) throws EOFException {
        if (offset + fieldSize > endIndex) {
            throw new EOFException();
        }
    }
}