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

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ByteArrayOutputStream extends OutputStream implements DataOutput {

    private final ByteArray array;
    private final DataOutputStream utf8out;
    private int offset;

    public ByteArrayOutputStream(ByteArray array, int startIndex) {
        if (array == null) {
            throw new NullPointerException("array");
        }
        this.array = array;
        if (startIndex < array.firstIndex()) {
            throw new ArrayIndexOutOfBoundsException(startIndex);
        }
        offset = startIndex;
        utf8out = new DataOutputStream(this);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        int newLen = Math.min(endOffset() - offset, len);
        array.set(offset, b, off, newLen);
        offset += newLen;
        if (newLen != len) {
            throw new IOException("overflow");
        }
    }

    @Override
    public void write(int b) throws IOException {
        if (offset >= endOffset()) {
            throw new IOException("overflow");
        }
        array.set8(offset ++, (byte) b);
    }

    public void writeBoolean(boolean v) throws IOException {
        write(v? (byte) 1 : (byte) 0);
    }

    public void writeByte(int v) throws IOException {
        write(v);
    }

    public void writeBytes(String s) throws IOException {
        int len = s.length();
        checkIndex(len);
        for (int i = 0; i < len; i ++) {
            write((byte) s.charAt(i));
        }
    }

    public void writeChar(int v) throws IOException {
        writeShort((short) v);
    }

    public void writeChars(String s) throws IOException {
        int len = s.length();
        checkIndex(len << 1);
        for (int i = 0 ; i < len ; i ++) {
            writeChar(s.charAt(i));
        }
    }

    public void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToLongBits(v));
    }

    public void writeFloat(float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    public void writeInt(int v) throws IOException {
        checkIndex(4);
        array.setBE32(offset, v);
        offset += 4;
    }

    public void writeLong(long v) throws IOException {
        checkIndex(8);
        array.setBE64(offset, v);
        offset += 8;
    }

    public void writeShort(int v) throws IOException {
        checkIndex(2);
        array.setBE16(offset, (short) v);
        offset += 2;
    }

    public void writeUTF(String s) throws IOException {
        utf8out.writeUTF(s);
    }

    public ByteArray array() {
        return array;
    }

    private int endOffset() {
        return array.firstIndex() + array.length();
    }

    private void checkIndex(int fieldSize) throws IOException {
        if (offset + fieldSize > endOffset()) {
            throw new IOException("overflow");
        }
    }
}
