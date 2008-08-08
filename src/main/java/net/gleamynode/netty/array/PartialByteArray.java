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


public abstract class PartialByteArray extends ByteArrayWrapper {

    private static ByteArray unwrap(ByteArray array) {
        if (array == null) {
            throw new NullPointerException("array");
        }
        // Unwrap as much as possible.
        for (;;) {
            if (array instanceof PartialByteArray) {
                array = ((PartialByteArray) array).unwrap();
            } else {
                break;
            }
        }
        return array;
    }

    protected PartialByteArray(ByteArray array) {
        super(unwrap(array));
    }

    @Override
    public abstract int firstIndex();

    @Override
    public abstract int endIndex();

    @Override
    public abstract int length();

    public void firstIndex(int firstIndex) {
        if (firstIndex < super.firstIndex()) {
            throw new IllegalArgumentException(
                    "firstIndex must be equal to or greater than " +
                    super.firstIndex() + ".");
        }
        if (firstIndex > super.endIndex()) {
            throw new IllegalArgumentException(
                    "firstIndex must be less than " + super.endIndex() + ".");
        }

        setLength(length() - (firstIndex - this.firstIndex()));
        setFirstIndex(firstIndex);
    }

    public void length(int length) {
        if (length > super.length() - (firstIndex() - super.firstIndex())) {
            throw new IllegalArgumentException(
                    "length must be equal to or less than " +
                    (super.length() - (firstIndex() - super.firstIndex())) + ".");
        }
        setLength(length);
    }

    protected abstract void setFirstIndex(int firstIndex);
    protected abstract void setLength(int length);

    @Override
    public byte get8(int index) {
        checkIndex(index);
        return super.get8(index);
    }

    @Override
    public short getBE16(int index) {
        checkIndex(index, 2);
        return super.getBE16(index);
    }

    @Override
    public int getBE24(int index) {
        checkIndex(index, 3);
        return super.getBE24(index);
    }

    @Override
    public int getBE32(int index) {
        checkIndex(index, 4);
        return super.getBE32(index);
    }

    @Override
    public long getBE48(int index) {
        checkIndex(index, 6);
        return super.getBE48(index);
    }

    @Override
    public long getBE64(int index) {
        checkIndex(index, 8);
        return super.getBE64(index);
    }

    @Override
    public short getLE16(int index) {
        checkIndex(index, 2);
        return super.getLE16(index);
    }

    @Override
    public int getLE24(int index) {
        checkIndex(index, 3);
        return super.getLE24(index);
    }

    @Override
    public int getLE32(int index) {
        checkIndex(index, 4);
        return super.getLE32(index);
    }

    @Override
    public long getLE48(int index) {
        checkIndex(index, 6);
        return super.getLE48(index);
    }

    @Override
    public long getLE64(int index) {
        checkIndex(index, 8);
        return super.getLE64(index);
    }

    @Override
    public void get(int index, ByteArray dst) {
        checkIndex(index, dst.length());
        super.get(index, dst);
    }

    @Override
    public void get(int index, ByteArray dst, int dstIndex, int length) {
        checkIndex(index, length);
        super.get(index, dst, dstIndex, length);
    }

    @Override
    public void get(int index, byte[] dst) {
        checkIndex(index, dst.length);
        super.get(index, dst);
    }

    @Override
    public void get(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        super.get(index, dst, dstIndex, length);
    }

    @Override
    public void get(int index, ByteBuffer dst) {
        checkIndex(index, dst.remaining());
        super.get(index, dst);
    }

    @Override
    public void set8(int index, byte value) {
        checkIndex(index);
        super.set8(index, value);
    }

    @Override
    public void setBE16(int index, short value) {
        checkIndex(index, 2);
        super.setBE16(index, value);
    }

    @Override
    public void setBE24(int index, int value) {
        checkIndex(index, 3);
        super.setBE24(index, value);
    }

    @Override
    public void setBE32(int index, int value) {
        checkIndex(index, 4);
        super.setBE32(index, value);
    }

    @Override
    public void setBE48(int index, long value) {
        checkIndex(index, 6);
        super.setBE48(index, value);
    }

    @Override
    public void setBE64(int index, long value) {
        checkIndex(index, 8);
        super.setBE64(index, value);
    }

    @Override
    public void setLE16(int index, short value) {
        checkIndex(index, 2);
        super.setLE16(index, value);
    }

    @Override
    public void setLE24(int index, int value) {
        checkIndex(index, 3);
        super.setLE24(index, value);
    }

    @Override
    public void setLE32(int index, int value) {
        checkIndex(index, 4);
        super.setLE32(index, value);
    }

    @Override
    public void setLE48(int index, long value) {
        checkIndex(index, 6);
        super.setLE48(index, value);
    }

    @Override
    public void setLE64(int index, long value) {
        checkIndex(index, 8);
        super.setLE64(index, value);
    }

    @Override
    public void set(int index, byte[] src) {
        checkIndex(index, src.length);
        super.set(index, src);
    }

    @Override
    public void set(int index, byte[] src, int srcIndex, int length) {
        checkIndex(index, length);
        super.set(index, src, srcIndex, length);
    }

    @Override
    public void set(int index, ByteArray src) {
        checkIndex(index, src.length());
        super.set(index, src);
    }

    @Override
    public void set(int index, ByteArray src, int srcIndex, int length) {
        checkIndex(index, length);
        super.set(index, src, srcIndex, length);
    }

    @Override
    public void set(int index, ByteBuffer src) {
        checkIndex(index, src.remaining());
        super.set(index, src);
    }

    @Override
    public void copyTo(OutputStream out) throws IOException {
        super.copyTo(out, firstIndex(), length());
    }

    @Override
    public void copyTo(OutputStream out, int index, int length)
            throws IOException {
        checkIndex(index, length);
        super.copyTo(out, index, length);
    }

    @Override
    public long indexOf(int fromIndex, byte value) {
        return ByteArrayUtil.indexOf(this, fromIndex, value);
    }

    @Override
    public long lastIndexOf(int fromIndex, byte value) {
        return ByteArrayUtil.lastIndexOf(this, fromIndex, value);
    }

    @Override
    public long indexOf(int fromIndex, ByteArrayIndexFinder indexFinder) {
        return ByteArrayUtil.indexOf(this, fromIndex, indexFinder);
    }

    @Override
    public long lastIndexOf(int fromIndex, ByteArrayIndexFinder indexFinder) {
        return ByteArrayUtil.lastIndexOf(this, fromIndex, indexFinder);

    }

    @Override
    public int copyTo(WritableByteChannel out) throws IOException {
        return super.copyTo(out, firstIndex(), length());
    }

    @Override
    public int copyTo(WritableByteChannel out, int index, int length)
            throws IOException {
        checkIndex(index, length);
        return super.copyTo(out, index, length);
    }

    @Override
    public ByteBuffer getByteBuffer() {
        return super.getByteBuffer(firstIndex(), length());
    }

    @Override
    public ByteBuffer getByteBuffer(int index, int length) {
        checkIndex(index, length);
        return super.getByteBuffer(index, length);
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
}
