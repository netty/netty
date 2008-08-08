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

public class ByteArrayInputStream extends InputStream implements DataInput {

    private final ByteArray array;
    private final int length;
    private int offset;
    private Integer mark;

    public ByteArrayInputStream(ByteArray array) {
        this(array, array.firstIndex(), array.length());
    }

    public ByteArrayInputStream(ByteArray array, int startIndex, int length) {
        if (array == null) {
            throw new NullPointerException("array");
        }
        if (startIndex < array.firstIndex()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (length < 0 || startIndex + length > array.endIndex()) {
            throw new ArrayIndexOutOfBoundsException();
        }

        this.array = array;
        offset = startIndex;
        this.length = length;
    }

    @Override
    public int available() throws IOException {
        return endOffset() - offset;
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
        if (offset >= endOffset()) {
            return -1;
        }
        return array.get8(offset ++) & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int available = available();
        if (available == 0) {
            return -1;
        }

        len = Math.min(available, len);
        array.get(offset, b, off, len);
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
        if (offset >= endOffset()) {
            throw new EOFException();
        }
        return array.get8(offset ++);
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
        array.get(offset, b, off, len);
        offset += len;
    }

    public int readInt() throws IOException {
        checkIndex(4);
        int answer = array.getBE32(offset);
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
        long answer = array.getBE64(offset);
        offset += 8;
        return answer;
    }

    public short readShort() throws IOException {
        checkIndex(2);
        short answer = array.getBE16(offset);
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

    private int endOffset() {
        return array.firstIndex() + length;
    }

    private void checkIndex(int fieldSize) throws EOFException {
        if (offset + fieldSize > endOffset()) {
            throw new EOFException();
        }
    }
}