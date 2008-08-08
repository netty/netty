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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;


public class ReadOnlyByteArray implements ByteArray {

    private final ByteArray array;

    public ReadOnlyByteArray(ByteArray array) {
        if (array == null) {
            throw new NullPointerException("array");
        }
        this.array = array;
    }

    public int firstIndex() {
        return array.firstIndex();
    }

    public byte get8(int i) {
        return array.get8(i);
    }

    public void get(int index, ByteArray dst, int dstIndex, int length) {
        array.get(index, dst, dstIndex, length);
    }

    public void get(int index, byte[] dst, int dstIndex, int length) {
        array.get(index, dst, dstIndex, length);
    }

    public void get(int index, ByteBuffer dst) {
        array.get(index, dst);
    }

    public short getBE16(int index) {
        return array.getBE16(index);
    }

    public int getBE24(int index) {
        return array.getBE24(index);
    }

    public int getBE32(int index) {
        return array.getBE32(index);
    }

    public long getBE48(int index) {
        return array.getBE48(index);
    }

    public long getBE64(int index) {
        return array.getBE64(index);
    }

    public short getLE16(int index) {
        return array.getLE16(index);
    }

    public int getLE24(int index) {
        return array.getLE24(index);
    }

    public int getLE32(int index) {
        return array.getLE32(index);
    }

    public long getLE48(int index) {
        return array.getLE48(index);
    }

    public long getLE64(int index) {
        return array.getLE64(index);
    }

    public boolean empty() {
        return array.empty();
    }

    public int length() {
        return array.length();
    }

    public void set8(int index, byte value) {
        throw new ReadOnlyByteArrayException();
    }

    public void set(int index, ByteArray src, int srcIndex, int length) {
        throw new ReadOnlyByteArrayException();
    }

    public void set(int index, byte[] src, int srcIndex, int length) {
        throw new ReadOnlyByteArrayException();
    }

    public void set(int index, ByteBuffer src) {
        throw new ReadOnlyByteArrayException();
    }

    public void setBE16(int index, short value) {
        throw new ReadOnlyByteArrayException();
    }

    public void setBE24(int index, int value) {
        throw new ReadOnlyByteArrayException();
    }

    public void setBE32(int index, int value) {
        throw new ReadOnlyByteArrayException();
    }

    public void setBE48(int index, long value) {
        throw new ReadOnlyByteArrayException();
    }

    public void setBE64(int index, long value) {
        throw new ReadOnlyByteArrayException();
    }

    public void setLE16(int index, short value) {
        throw new ReadOnlyByteArrayException();
    }

    public void setLE24(int index, int value) {
        throw new ReadOnlyByteArrayException();
    }

    public void setLE32(int index, int value) {
        throw new ReadOnlyByteArrayException();
    }

    public void setLE48(int index, long value) {
        throw new ReadOnlyByteArrayException();
    }

    public void setLE64(int index, long value) {
        throw new ReadOnlyByteArrayException();
    }

    public void get(int index, ByteArray dst) {
        array.get(index, dst);
    }

    public void get(int index, byte[] dst) {
        array.get(index, dst);
    }

    public int endIndex() {
        return array.endIndex();
    }

    public void set(int index, ByteArray src) {
        throw new ReadOnlyByteArrayException();
    }

    public void set(int index, byte[] src) {
        throw new ReadOnlyByteArrayException();
    }

    public void copyTo(OutputStream out) throws IOException {
        array.copyTo(out);
    }

    public void copyTo(OutputStream out, int index, int length)
            throws IOException {
        array.copyTo(out, index, length);
    }

    public int copyTo(WritableByteChannel out) throws IOException {
        return array.copyTo(out);
    }

    public int copyTo(WritableByteChannel out, int index, int length)
            throws IOException {
        return array.copyTo(out, index, length);
    }
}
