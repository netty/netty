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
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 *
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.uses net.gleamynode.netty.array.ByteArrayUtil
 */
public abstract class AbstractByteArray implements ByteArray {

    private int hashCode;

    public boolean empty() {
        return length() == 0;
    }

    public int endIndex() {
        return firstIndex() + length();
    }

    public void get(int index, byte[] dst) {
        get(index, dst, 0, dst.length);
    }

    public void get(int index, ByteArray dst) {
        get(index, dst, dst.firstIndex(), dst.length());
    }

    public void set(int index, byte[] src) {
        set(index, src, 0, src.length);
    }

    public void set(int index, ByteArray src) {
        set(index, src, src.firstIndex(), src.length());
    }

    public long indexOf(int fromIndex, byte value) {
        return ByteArrayUtil.indexOf(this, fromIndex, value);
    }

    public long lastIndexOf(int fromIndex, byte value) {
        return ByteArrayUtil.lastIndexOf(this, fromIndex, value);

    }

    public long indexOf(int fromIndex, ByteArrayIndexFinder indexFinder) {
        return ByteArrayUtil.indexOf(this, fromIndex, indexFinder);
    }

    public long lastIndexOf(int fromIndex, ByteArrayIndexFinder indexFinder) {
        return ByteArrayUtil.lastIndexOf(this, fromIndex, indexFinder);

    }

    public void copyTo(OutputStream out) throws IOException {
        copyTo(out, firstIndex(), length());
    }

    public int copyTo(WritableByteChannel out) throws IOException {
        return copyTo(out, firstIndex(), length());
    }

    public int copyTo(GatheringByteChannel out) throws IOException {
        return copyTo(out, firstIndex(), length());
    }

    public int copyTo(GatheringByteChannel out, int index, int length) throws IOException {
        return copyTo((WritableByteChannel) out, index, length);
    }

    public ByteBuffer getByteBuffer() {
        return getByteBuffer(firstIndex(), length());
    }

    @Override
    public int hashCode() {
        if (hashCode != 0) {
            return hashCode;
        }
        return hashCode = ByteArrayUtil.hashCode(this);
    }

    protected void clearHashCode() {
        hashCode = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ByteArray)) {
            return false;
        }
        return ByteArrayUtil.equals(this, (ByteArray) o);
    }

    public int compareTo(ByteArray that) {
        return ByteArrayUtil.compare(this, that);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' +
               "firstOffset=" + firstIndex() + ", " +
               "length=" + length() +
               ')';
    }
}
