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
package net.gleamynode.netty.array;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class ByteArrayBufferInputStream extends InputStream implements DataInput {

    private final ByteArrayBuffer buffer;
    private final int length;

    public ByteArrayBufferInputStream(ByteArrayBuffer buffer) {
        this(buffer, buffer.length());
    }

    public ByteArrayBufferInputStream(ByteArrayBuffer buffer, int length) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        if (length < 0 || buffer.firstIndex() + length > buffer.endIndex()) {
            throw new ArrayIndexOutOfBoundsException();
        }

        this.buffer = buffer;
        this.length = length;
    }

    @Override
    public int available() throws IOException {
        return endOffset() - buffer.firstIndex();
    }

    @Override
    public int read() throws IOException {
        if (buffer.firstIndex() >= endOffset()) {
            return -1;
        }
        return buffer.read8() & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        len = Math.min(available(), len);
        ByteArray a = buffer.read(len);
        a.get(a.firstIndex(), b, off, len);
        return len;
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
        int b = read();
        if (b < 0) {
            throw new EOFException();
        }
        return (byte) b;
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
        ByteArray a = buffer.read(len);
        a.get(a.firstIndex(), b, off, len);
    }

    public int readInt() throws IOException {
        checkIndex(4);
        return buffer.readBE32();
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
        return buffer.readBE64();
    }

    public short readShort() throws IOException {
        checkIndex(2);
        return buffer.readBE16();
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
        buffer.skip(nBytes);
        return nBytes;
    }

    private int endOffset() {
        return buffer.firstIndex() + length;
    }

    private void checkIndex(int fieldSize) throws EOFException {
        if (buffer.firstIndex() + fieldSize > endOffset()) {
            throw new EOFException();
        }
    }
}