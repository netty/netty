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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.array.ByteBufferBackedByteArray;
import net.gleamynode.netty.array.CompositeByteArray;
import net.gleamynode.netty.array.DynamicPartialByteArray;
import net.gleamynode.netty.array.HeapByteArray;
import net.gleamynode.netty.array.PartialByteArray;

public class CompositeByteArrayBuffer implements ByteArrayBuffer {

    private static final int DEFAULT_CAPACITY_INCREMENT = 512;

    final ObjectBufferListenerSupport<ByteArray> listenerSupport =
        new ObjectBufferListenerSupport<ByteArray>(this);

    private final CompositeByteArray array;
    private final int capacityIncrement;
    private DynamicPartialByteArray tail;

    public CompositeByteArrayBuffer() {
        this(DEFAULT_CAPACITY_INCREMENT);
    }

    public CompositeByteArrayBuffer(int capacityIncrement) {
        if (capacityIncrement < 8) {
            throw new IllegalArgumentException(
                    "capacityIncrement should be equal to or greater than 8.");
        }
        this.capacityIncrement = capacityIncrement;

        // Create a new CompositeByteArray which notifies listeners.
        array = new CompositeByteArray() {
            @Override
            public void addFirst(ByteArray array) {
                listenerSupport.assureAcceptance(array);
                super.addFirst(array);
                listenerSupport.notifyAddition(array);
            }

            @Override
            public void addLast(ByteArray array) {
                listenerSupport.assureAcceptance(array);
                super.addLast(array);
                listenerSupport.notifyAddition(array);
            }

            @Override
            public ByteArray removeFirst() {
                ByteArray first = super.removeFirst();
                listenerSupport.notifyRemoval(first);
                return first;
            }

            @Override
            public ByteArray removeLast() {
                ByteArray last = super.removeLast();
                listenerSupport.notifyRemoval(last);
                return last;
            }
        };
    }

    public boolean empty() {
        return array.empty();
    }

    public int firstIndex() {
        return array.firstIndex();
    }

    public void get(int index, byte[] dst, int dstIndex, int length) {
        array.get(index, dst, dstIndex, length);
    }

    public void get(int index, byte[] dst) {
        array.get(index, dst);
    }

    public void get(int index, ByteArray dst, int dstIndex, int length) {
        array.get(index, dst, dstIndex, length);
    }

    public void get(int index, ByteArray dst) {
        array.get(index, dst);
    }

    public void get(int index, ByteBuffer dst) {
        array.get(index, dst);
    }

    public byte get8(int index) {
        return array.get8(index);
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

    public int endIndex() {
        return array.endIndex();
    }

    public int length() {
        return array.length();
    }

    public void set(int index, byte[] src, int srcIndex, int length) {
        array.set(index, src, srcIndex, length);
    }

    public void set(int index, byte[] src) {
        array.set(index, src);
    }

    public void set(int index, ByteArray src, int srcIndex, int length) {
        array.set(index, src, srcIndex, length);
    }

    public void set(int index, ByteArray src) {
        array.set(index, src);
    }

    public void set(int index, ByteBuffer src) {
        array.set(index, src);
    }

    public void set8(int index, byte value) {
        array.set8(index, value);
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
    public String toString() {
        return array.toString();
    }

    public void addListener(ObjectBufferListener<? super ByteArray> listener) {
        listenerSupport.addListener(listener);
    }

    public void removeListener(ObjectBufferListener<? super ByteArray> listener) {
        listenerSupport.removeListener(listener);
    }

    public int count() {
        return array.count();
    }

    public ByteArray elementAt(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }
        int i = 0;
        for (ByteArray a: array) {
            if (i == index) {
                return a;
            }
            i ++;
        }

        throw new IndexOutOfBoundsException(String.valueOf(index));
    }

    public ByteArray read() {
        return array.removeFirst();
    }

    public Iterator<ByteArray> iterator() {
        return array.iterator();
    }

    public ByteArray read(int length) {
        return array.removeFirst(length);
    }

    public void skip(int length) {
        array.discardFirst(length);
    }

    public byte read8() {
        DynamicPartialByteArray first = (DynamicPartialByteArray) array.first();
        byte value = first.get8(first.firstIndex());
        if (first.length() > 1) {
            first.firstIndex(first.firstIndex() + 1);
        } else {
            array.removeFirst();
        }
        return value;
    }

    public short readBE16() {
        DynamicPartialByteArray first = (DynamicPartialByteArray) array.first();
        short value;
        if (first.length() > 2) {
            value = first.getBE16(first.firstIndex());
            first.firstIndex(first.firstIndex() + 2);
        } else if (first.length() == 2) {
            value = first.getBE16(first.firstIndex());
            array.removeFirst();
        } else {
            value = array.getBE16(array.firstIndex());
            array.discardFirst(2);
        }
        return value;
    }

    public int readBE24() {
        DynamicPartialByteArray first = (DynamicPartialByteArray) array.first();
        int value;
        if (first.length() > 3) {
            value = first.getBE24(first.firstIndex());
            first.firstIndex(first.firstIndex() + 3);
        } else if (first.length() == 3) {
            value = first.getBE24(first.firstIndex());
            array.removeFirst();
        } else {
            value = array.getBE24(array.firstIndex());
            array.discardFirst(3);
        }
        return value;
    }

    public int readBE32() {
        DynamicPartialByteArray first = (DynamicPartialByteArray) array.first();
        int value;
        if (first.length() > 4) {
            value = first.getBE32(first.firstIndex());
            first.firstIndex(first.firstIndex() + 4);
        } else if (first.length() == 4) {
            value = first.getBE32(first.firstIndex());
            array.removeFirst();
        } else {
            value = array.getBE32(array.firstIndex());
            array.discardFirst(4);
        }
        return value;
    }

    public long readBE48() {
        DynamicPartialByteArray first = (DynamicPartialByteArray) array.first();
        long value;
        if (first.length() > 6) {
            value = first.getBE48(first.firstIndex());
            first.firstIndex(first.firstIndex() + 6);
        } else if (first.length() == 6) {
            value = first.getBE48(first.firstIndex());
            array.removeFirst();
        } else {
            value = array.getBE48(array.firstIndex());
            array.discardFirst(6);
        }
        return value;
    }

    public long readBE64() {
        DynamicPartialByteArray first = (DynamicPartialByteArray) array.first();
        long value;
        if (first.length() > 8) {
            value = first.getBE64(first.firstIndex());
            first.firstIndex(first.firstIndex() + 8);
        } else if (first.length() == 8) {
            value = first.getBE64(first.firstIndex());
            array.removeFirst();
        } else {
            value = array.getBE64(array.firstIndex());
            array.discardFirst(8);
        }
        return value;
    }

    public short readLE16() {
        DynamicPartialByteArray first = (DynamicPartialByteArray) array.first();
        short value;
        if (first.length() > 2) {
            value = first.getLE16(first.firstIndex());
            first.firstIndex(first.firstIndex() + 2);
        } else if (first.length() == 2) {
            value = first.getLE16(first.firstIndex());
            array.removeFirst();
        } else {
            value = array.getLE16(array.firstIndex());
            array.discardFirst(2);
        }
        return value;
    }

    public int readLE24() {
        DynamicPartialByteArray first = (DynamicPartialByteArray) array.first();
        int value;
        if (first.length() > 3) {
            value = first.getLE24(first.firstIndex());
            first.firstIndex(first.firstIndex() + 3);
        } else if (first.length() == 3) {
            value = first.getLE24(first.firstIndex());
            array.removeFirst();
        } else {
            value = array.getLE24(array.firstIndex());
            array.discardFirst(3);
        }
        return value;
    }

    public int readLE32() {
        DynamicPartialByteArray first = (DynamicPartialByteArray) array.first();
        int value;
        if (first.length() > 4) {
            value = first.getLE32(first.firstIndex());
            first.firstIndex(first.firstIndex() + 4);
        } else if (first.length() == 4) {
            value = first.getLE32(first.firstIndex());
            array.removeFirst();
        } else {
            value = array.getLE32(array.firstIndex());
            array.discardFirst(4);
        }
        return value;
    }

    public long readLE48() {
        DynamicPartialByteArray first = (DynamicPartialByteArray) array.first();
        long value;
        if (first.length() > 6) {
            value = first.getLE48(first.firstIndex());
            first.firstIndex(first.firstIndex() + 6);
        } else if (first.length() == 6) {
            value = first.getLE48(first.firstIndex());
            array.removeFirst();
        } else {
            value = array.getLE48(array.firstIndex());
            array.discardFirst(6);
        }
        return value;
    }

    public long readLE64() {
        DynamicPartialByteArray first = (DynamicPartialByteArray) array.first();
        long value;
        if (first.length() > 8) {
            value = first.getLE64(first.firstIndex());
            first.firstIndex(first.firstIndex() + 8);
        } else if (first.length() == 8) {
            value = first.getLE64(first.firstIndex());
            array.removeFirst();
        } else {
            value = array.getLE64(array.firstIndex());
            array.discardFirst(8);
        }
        return value;
    }

    public void write(byte[] src, int srcIndex, int length) {
        tail = null;
        if (srcIndex == 0 && src.length == length) {
            array.addLast(new HeapByteArray(src));
        } else {
            array.addLast(new PartialByteArray(new HeapByteArray(src), srcIndex, length));
        }
    }

    public void write(byte[] src) {
        tail = null;
        array.addLast(new HeapByteArray(src));
    }

    public void write(ByteArray src, int srcIndex, int length) {
        tail = null;
        if (src.firstIndex() == srcIndex && src.length() == length) {
            array.addLast(src);
        } else {
            array.addLast(new PartialByteArray(src, srcIndex, length));
        }
    }

    public void write(ByteArray src) {
        tail = null;
        array.addLast(src);
    }

    public void write(ByteBuffer src) {
        ByteArray newArray;
        if (src.isReadOnly()) {
            newArray = new ByteBufferBackedByteArray(src);
        } else if (src.hasArray()) {
            byte[] a = src.array();
            if (src.arrayOffset() == 0 && src.position() == 0 &&
                src.limit() == src.capacity() && a.length == src.capacity()) {
                newArray = new HeapByteArray(a);
            } else {
                newArray = new PartialByteArray(
                        new HeapByteArray(a),
                        src.position() + src.arrayOffset(),
                        src.remaining());
            }
        } else if (src.isDirect()) {
            newArray = new ByteBufferBackedByteArray(src);
        } else {
            newArray = new ByteBufferBackedByteArray(src);
        }

        tail = null;
        array.addLast(newArray);
    }

    public void write8(byte value) {
        DynamicPartialByteArray tail = tail(1);
        tail.set8(tail.endIndex() - 1, value);
    }

    public void writeBE16(short value) {
        DynamicPartialByteArray tail = tail(2);
        tail.setBE16(tail.endIndex() - 2, value);
    }

    public void writeBE24(int value) {
        DynamicPartialByteArray tail = tail(3);
        tail.setBE24(tail.endIndex() - 3, value);
    }

    public void writeBE32(int value) {
        DynamicPartialByteArray tail = tail(4);
        tail.setBE32(tail.endIndex() - 4, value);
    }

    public void writeBE48(long value) {
        DynamicPartialByteArray tail = tail(6);
        tail.setBE48(tail.endIndex() - 6, value);
    }

    public void writeBE64(long value) {
        DynamicPartialByteArray tail = tail(8);
        tail.setBE64(tail.endIndex() - 8, value);
    }

    public void writeLE16(short value) {
        DynamicPartialByteArray tail = tail(2);
        tail.setLE16(tail.endIndex() - 2, value);
    }

    public void writeLE24(int value) {
        DynamicPartialByteArray tail = tail(3);
        tail.setLE24(tail.endIndex() - 3, value);
    }

    public void writeLE32(int value) {
        DynamicPartialByteArray tail = tail(4);
        tail.setLE32(tail.endIndex() - 4, value);
    }

    public void writeLE48(long value) {
        DynamicPartialByteArray tail = tail(6);
        tail.setLE48(tail.endIndex() - 6, value);
    }

    public void writeLE64(long value) {
        DynamicPartialByteArray tail = tail(8);
        tail.setLE64(tail.endIndex() - 8, value);
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

    private DynamicPartialByteArray tail(int length) {
        DynamicPartialByteArray oldTail = tail;
        if (oldTail == null || oldTail.unwrap().endIndex() - oldTail.endIndex() < length) {
            // TODO Needs ByteArrayFactory.
            ByteArray newTail = new HeapByteArray(capacityIncrement);
            array.addLast(newTail);

            // Set the length of the tail to zero first.
            // Each write operation will increase the length.
            DynamicPartialByteArray newDynamicTail =
                (DynamicPartialByteArray) array.last();
            newDynamicTail.length(length);

            tail = newDynamicTail;
            return newDynamicTail;
        } else {
            oldTail.length(oldTail.length() + length);
            return oldTail;
        }
    }
}
