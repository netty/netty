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


public class PartialByteArray extends AbstractByteArray {

    private final ByteArray array;
    private final int firstIndex;
    private final int length;

    public PartialByteArray(ByteArray array, int firstIndex, int length) {
        if (array == null) {
            throw new NullPointerException("array");
        }
        if (firstIndex < array.firstIndex()) {
            throw new IllegalArgumentException(
                    "firstIndex must be equal to or greater than " +
                    array.firstIndex() + ".");
        }
        if (length > array.length() - (firstIndex - array.firstIndex())) {
            throw new IllegalArgumentException(
                    "length must be equal to or less than " +
                    (array.length() - (firstIndex - array.firstIndex())) + ".");
        }

        // Unwrap if possible.
        if (array instanceof PartialByteArray) {
            array = ((PartialByteArray) array).unwrap();
        }
        if (array instanceof DynamicPartialByteArray) {
            array = ((DynamicPartialByteArray) array).unwrap();
        }

        this.array = array;
        this.firstIndex = firstIndex;
        this.length = length;
    }


    public ByteArray unwrap() {
        return array;
    }

    public int firstIndex() {
        return firstIndex;
    }

    public int length() {
        return length;
    }

    public byte get8(int index) {
        checkIndex(index);
        return array.get8(index);
    }

    public short getBE16(int index) {
        checkIndex(index, 2);
        return array.getBE16(index);
    }

    public int getBE24(int index) {
        checkIndex(index, 3);
        return array.getBE24(index);
    }

    public int getBE32(int index) {
        checkIndex(index, 4);
        return array.getBE32(index);
    }

    public long getBE48(int index) {
        checkIndex(index, 6);
        return array.getBE48(index);
    }

    public long getBE64(int index) {
        checkIndex(index, 8);
        return array.getBE64(index);
    }

    public short getLE16(int index) {
        checkIndex(index, 2);
        return array.getLE16(index);
    }

    public int getLE24(int index) {
        checkIndex(index, 3);
        return array.getLE24(index);
    }

    public int getLE32(int index) {
        checkIndex(index, 4);
        return array.getLE32(index);
    }

    public long getLE48(int index) {
        checkIndex(index, 6);
        return array.getLE48(index);
    }

    public long getLE64(int index) {
        checkIndex(index, 8);
        return array.getLE64(index);
    }

    public void get(int index, ByteArray dst, int dstIndex, int length) {
        checkIndex(index, length);
        array.get(index, dst, dstIndex, length);
    }

    public void get(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        array.get(index, dst, dstIndex, length);
    }

    public void get(int index, ByteBuffer dst) {
        checkIndex(index, dst.remaining());
        array.get(index, dst);
    }

    public void set8(int index, byte value) {
        checkIndex(index);
        array.set8(index, value);
    }

    public void setBE16(int index, short value) {
        checkIndex(index, 2);
        array.setBE16(index, value);
    }

    public void setBE24(int index, int value) {
        checkIndex(index, 3);
        array.setBE24(index, value);
    }

    public void setBE32(int index, int value) {
        checkIndex(index, 4);
        array.setBE32(index, value);
    }

    public void setBE48(int index, long value) {
        checkIndex(index, 6);
        array.setBE48(index, value);
    }

    public void setBE64(int index, long value) {
        checkIndex(index, 8);
        array.setBE64(index, value);
    }

    public void setLE16(int index, short value) {
        checkIndex(index, 2);
        array.setLE16(index, value);
    }

    public void setLE24(int index, int value) {
        checkIndex(index, 3);
        array.setLE24(index, value);
    }

    public void setLE32(int index, int value) {
        checkIndex(index, 4);
        array.setLE32(index, value);
    }

    public void setLE48(int index, long value) {
        checkIndex(index, 6);
        array.setLE48(index, value);
    }

    public void setLE64(int index, long value) {
        checkIndex(index, 8);
        array.setLE64(index, value);
    }

    public void set(int index, byte[] src, int srcIndex, int length) {
        checkIndex(index, length);
        array.set(index, src, srcIndex, length);
    }

    public void set(int index, ByteArray src, int srcIndex, int length) {
        checkIndex(index, length);
        array.set(index, src, srcIndex, length);
    }

    public void set(int index, ByteBuffer src) {
        checkIndex(index, src.remaining());
        array.set(index, src);
    }

    private void checkIndex(int index) {
        if (index < firstIndex() || index >= endIndex()) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
    }

    private void checkIndex(int startIndex, int length) {
        if (length < 0) {
            throw new IllegalArgumentException(
                    "length is negative: " + length);
        }
        if (startIndex < firstIndex()) {
            throw new ArrayIndexOutOfBoundsException(startIndex);
        }
        if (length > length()) {
            throw new ArrayIndexOutOfBoundsException(startIndex + length);
        }
    }

    public void copyTo(OutputStream out, int index, int length)
            throws IOException {
        checkIndex(index, length);
        array.copyTo(out, index, length);
    }

    public int copyTo(WritableByteChannel out, int index, int length)
            throws IOException {
        checkIndex(index, length);
        return array.copyTo(out, index, length);
    }
}
