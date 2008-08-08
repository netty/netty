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

    private final CompositeByteArray array;
    private final boolean zeroCopy;
    private final DataOutputStream utf8out = new DataOutputStream(this);

    public ByteArrayOutputStream() {
        this(CompositeByteArray.DEFAULT_CAPACITY_INCREMENT, false);
    }

    public ByteArrayOutputStream(int capacityIncrement, boolean zeroCopy) {
        array = new CompositeByteArray(capacityIncrement);
        this.zeroCopy = zeroCopy;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return;
        }

        if (zeroCopy) {
            array.write(b, off, len);
        } else {
            byte[] copy = new byte[len];
            System.arraycopy(b, off, copy, 0, len);
            array.write(copy);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        array.write(zeroCopy? b : b.clone());
    }

    @Override
    public void write(int b) throws IOException {
        array.write8((byte) b);
    }

    public void writeBoolean(boolean v) throws IOException {
        write(v? (byte) 1 : (byte) 0);
    }

    public void writeByte(int v) throws IOException {
        write(v);
    }

    public void writeBytes(String s) throws IOException {
        int len = s.length();
        for (int i = 0; i < len; i ++) {
            write((byte) s.charAt(i));
        }
    }

    public void writeChar(int v) throws IOException {
        writeShort((short) v);
    }

    public void writeChars(String s) throws IOException {
        int len = s.length();
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
        array.writeBE32(v);
    }

    public void writeLong(long v) throws IOException {
        array.writeBE64(v);
    }

    public void writeShort(int v) throws IOException {
        array.writeBE16((short) v);
    }

    public void writeUTF(String s) throws IOException {
        utf8out.writeUTF(s);
    }

    public ByteArray array() {
        return array;
    }
}
