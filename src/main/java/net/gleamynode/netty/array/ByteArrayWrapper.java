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


public class ByteArrayWrapper extends AbstractByteArray {

    private final ByteArray array;

    protected ByteArrayWrapper(ByteArray array) {
        if (array == null) {
            throw new NullPointerException("array");
        }
        this.array = array;
    }

    public ByteArray unwrap() {
        return array;
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

    @Override
    public boolean empty() {
        return array.empty();
    }

    public int length() {
        return array.length();
    }

    public void set8(int index, byte value) {
        array.set8(index, value);
    }

    public void set(int index, ByteArray src, int srcIndex, int length) {
        array.set(index, src, srcIndex, length);
    }

    public void set(int index, byte[] src, int srcIndex, int length) {
        array.set(index, src, srcIndex, length);
    }

    public void set(int index, ByteBuffer src) {
        array.set(index, src);
    }

    public void setBE16(int index, short value) {
        array.setBE16(index, value);
    }

    public void setBE24(int index, int value) {
        array.setBE24(index, value);
    }

    public void setBE32(int index, int value) {
        array.setBE32(index, value);
    }

    public void setBE48(int index, long value) {
        array.setBE48(index, value);
    }

    public void setBE64(int index, long value) {
        array.setBE64(index, value);
    }

    public void setLE16(int index, short value) {
        array.setLE16(index, value);
    }

    public void setLE24(int index, int value) {
        array.setLE24(index, value);
    }

    public void setLE32(int index, int value) {
        array.setLE32(index, value);
    }

    public void setLE48(int index, long value) {
        array.setLE48(index, value);
    }

    public void setLE64(int index, long value) {
        array.setLE64(index, value);
    }

    @Override
    public void get(int index, ByteArray dst) {
        array.get(index, dst);
    }

    @Override
    public void get(int index, byte[] dst) {
        array.get(index, dst);
    }

    @Override
    public int endIndex() {
        return array.endIndex();
    }

    @Override
    public void set(int index, ByteArray src) {
        array.set(index, src);
    }

    @Override
    public void set(int index, byte[] src) {
        array.set(index, src);
    }

    @Override
    public long indexOf(int fromIndex, byte value) {
        return array.indexOf(fromIndex, value);
    }

    @Override
    public long indexOf(int fromIndex, ByteArrayIndexFinder indexFinder) {
        return array.indexOf(fromIndex, indexFinder);
    }

    @Override
    public long lastIndexOf(int fromIndex, byte value) {
        return array.lastIndexOf(fromIndex, value);
    }

    @Override
    public long lastIndexOf(int fromIndex, ByteArrayIndexFinder indexFinder) {
        return array.lastIndexOf(fromIndex, indexFinder);
    }

    @Override
    public void copyTo(OutputStream out) throws IOException {
        array.copyTo(out);
    }

    public void copyTo(OutputStream out, int index, int length)
            throws IOException {
        array.copyTo(out, index, length);
    }

    @Override
    public int copyTo(WritableByteChannel out) throws IOException {
        return array.copyTo(out);
    }

    public int copyTo(WritableByteChannel out, int index, int length)
            throws IOException {
        return array.copyTo(out, index, length);
    }

    @Override
    public int copyTo(GatheringByteChannel out) throws IOException {
        return array.copyTo(out);
    }

    @Override
    public int copyTo(GatheringByteChannel out, int index, int length)
            throws IOException {
        return array.copyTo(out, index, length);
    }

    @Override
    public ByteBuffer getByteBuffer() {
        return array.getByteBuffer();
    }

    public ByteBuffer getByteBuffer(int index, int length) {
        return array.getByteBuffer(index, length);
    }

    @Override
    public int compareTo(ByteArray o) {
        return array.compareTo(o);
    }
}
