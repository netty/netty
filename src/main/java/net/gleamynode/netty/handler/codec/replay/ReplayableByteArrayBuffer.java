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
package net.gleamynode.netty.handler.codec.replay;

import static net.gleamynode.netty.handler.codec.replay.ReplayingDecoder.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.array.ByteArrayIndexFinder;
import net.gleamynode.netty.array.CompositeByteArray;
import net.gleamynode.netty.array.HeapByteArray;
import net.gleamynode.netty.array.ReadOnlyByteArrayBuffer;


/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class ReplayableByteArrayBuffer extends ReadOnlyByteArrayBuffer implements Iterable<ByteArray> {

    private final List<Object> returnValues = new ArrayList<Object>(128);
    private int currentIndex;
    private int firstIndex;

    public ReplayableByteArrayBuffer() {
        super(new CompositeByteArray());
    }

    public void rewind() {
        clearIndices();
        throw REWIND;
    }

    public void clear() {
        clearIndices();
        returnValues.clear();
    }

    public boolean isReplaying() {
        return currentIndex < returnValues.size();
    }

    private Object replay() {
        return returnValues.get(currentIndex ++);
    }

    private <T> T record(T returnValue) {
        returnValues.add(returnValue);
        currentIndex ++;
        return returnValue;
    }

    private void clearIndices() {
        currentIndex = 0;
        firstIndex = 0;
    }

    @Override
    public ByteArray read() {
        ByteArray a;
        if (isReplaying()) {
            a = (ByteArray) replay();
        } else if (empty()) {
            rewind();
            return null;
        } else {
            a = record(super.read());
        }

        firstIndex += a.length();
        return a;
    }

    @Override
    public ByteArray read(ByteArrayIndexFinder endIndexFinder) {
        ByteArray a;
        if (isReplaying()) {
            a = (ByteArray) replay();
        } else if (super.indexOf(0, endIndexFinder) == NOT_FOUND) {
            rewind();
            return null;
        } else {
            a = record(super.read(endIndexFinder));
        }

        firstIndex += a.length();
        return a;
    }

    @Override
    public ByteArray read(int length) {
        ByteArray a;
        if (isReplaying()) {
            a = (ByteArray) replay();
            if (a.length() != length) {
                throw new IllegalStateException("mismatching request");
            }
        } else if (length > super.length()) {
            rewind();
            return null;
        } else {
            a = record(super.read(length));
        }

        firstIndex += length;
        return a;
    }

    @Override
    public byte read8() {
        byte b;
        if (isReplaying()) {
            b = ((Byte) replay()).byteValue();
        } else if (empty()) {
            rewind();
            return 0;
        } else {
            b = record(Byte.valueOf(super.read8()));
        }

        firstIndex ++;
        return b;
    }

    @Override
    public short readBE16() {
        short v;
        if (isReplaying()) {
            v = ((Short) replay()).shortValue();
        } else if (super.length() < 2) {
            rewind();
            return 0;
        } else {
            v = record(Short.valueOf(super.readBE16()));
        }

        firstIndex += 2;
        return v;
    }

    @Override
    public int readBE24() {
        int v;
        if (isReplaying()) {
            v = ((Integer) replay()).intValue();
        } else if (super.length() < 3) {
            rewind();
            return 0;
        } else {
            v = record(Integer.valueOf(super.readBE24()));
        }

        firstIndex += 3;
        return v;
    }

    @Override
    public int readBE32() {
        int v;
        if (isReplaying()) {
            v = ((Integer) replay()).intValue();
        } else if (super.length() < 4) {
            rewind();
            return 0;
        } else {
            v = record(Integer.valueOf(super.readBE32()));
        }

        firstIndex += 4;
        return v;
    }

    @Override
    public long readBE48() {
        long v;
        if (isReplaying()) {
            v = ((Long) replay()).longValue();
        } else if (super.length() < 6) {
            rewind();
            return 0;
        } else {
            v = record(Long.valueOf(super.readBE48()));
        }

        firstIndex += 6;
        return v;
    }

    @Override
    public long readBE64() {
        long v;
        if (isReplaying()) {
            v = ((Long) replay()).longValue();
        } else if (super.length() < 8) {
            rewind();
            return 0;
        } else {
            v = record(Long.valueOf(super.readBE64()));
        }

        firstIndex += 8;
        return v;
    }

    @Override
    public short readLE16() {
        short v;
        if (isReplaying()) {
            v = ((Short) replay()).shortValue();
        } else if (super.length() < 2) {
            rewind();
            return 0;
        } else {
            v = record(Short.valueOf(super.readLE16()));
        }

        firstIndex += 2;
        return v;
    }

    @Override
    public int readLE24() {
        int v;
        if (isReplaying()) {
            v = ((Integer) replay()).intValue();
        } else if (super.length() < 3) {
            rewind();
            return 0;
        } else {
            v = record(Integer.valueOf(super.readLE24()));
        }

        firstIndex += 3;
        return v;
    }

    @Override
    public int readLE32() {
        int v;
        if (isReplaying()) {
            v = ((Integer) replay()).intValue();
        } else if (super.length() < 4) {
            rewind();
            return 0;
        } else {
            v = record(Integer.valueOf(super.readLE32()));
        }

        firstIndex += 4;
        return v;
    }

    @Override
    public long readLE48() {
        long v;
        if (isReplaying()) {
            v = ((Long) replay()).longValue();
        } else if (super.length() < 6) {
            rewind();
            return 0;
        } else {
            v = record(Long.valueOf(super.readLE48()));
        }

        firstIndex += 6;
        return v;
    }

    @Override
    public long readLE64() {
        long v;
        if (isReplaying()) {
            v = ((Long) replay()).longValue();
        } else if (super.length() < 8) {
            rewind();
            return 0;
        } else {
            v = record(Long.valueOf(super.readLE64()));
        }

        firstIndex += 8;
        return v;
    }

    @Override
    public void skip(ByteArrayIndexFinder firstIndexFinder) {
        int newFirstIndex;
        if (isReplaying()) {
            newFirstIndex = ((Integer) replay()).intValue();
        } else if (super.indexOf(0, firstIndexFinder) == NOT_FOUND) {
            rewind();
            return;
        } else {
            super.skip(firstIndexFinder);
            newFirstIndex = record(Integer.valueOf(super.firstIndex()));
        }

        firstIndex = newFirstIndex;
    }

    @Override
    public void skip(int length) {
        if (isReplaying()) {
            int actualLength = ((Integer) replay()).intValue();
            if (actualLength != length) {
                throw new IllegalStateException("mismatching request");
            }
        } else if (length > super.length()) {
            rewind();
            return;
        } else {
            super.skip(length);
            record(Integer.valueOf(length));
        }

        firstIndex += length;
    }

    @Override
    public void get(int index, byte[] dst, int dstIndex, int length) {
        if (isReplaying()) {
            byte[] src = (byte[]) replay();
            if (src.length != length) {
                throw new IllegalStateException("mismatching request");
            }
            System.arraycopy(src, 0, dst, dstIndex, length);
        } else if (index < super.firstIndex() || dstIndex + length > dst.length) {
            throw new NoSuchElementException();
        } else if (index + length > super.endIndex()) {
            rewind();
        } else {
            byte[] src = new byte[length];
            super.get(index, src, 0, length);
            System.arraycopy(src, 0, dst, dstIndex, length);
            record(src);
        }
    }

    @Override
    public void get(int index, byte[] dst) {
        get(index, dst, 0, dst.length);
    }

    @Override
    public void get(int index, ByteArray dst, int dstIndex, int length) {
        if (isReplaying()) {
            ByteArray src = (ByteArray) replay();
            if (src.length() != length) {
                throw new IllegalStateException("mismatching request");
            }
            src.get(0, dst, dstIndex, length);
        } else if (index < super.firstIndex() || dstIndex + length > dst.endIndex()) {
            throw new NoSuchElementException();
        } else if (index + length > super.endIndex()) {
            rewind();
        } else {
            ByteArray src = new HeapByteArray(length);
            super.get(index, src, 0, length);
            src.get(0, dst, dstIndex, length);
            record(src);
        }
    }

    @Override
    public void get(int index, ByteArray dst) {
        get(index, dst, dst.firstIndex(), dst.endIndex());
    }

    @Override
    public void get(int index, ByteBuffer dst) {
        if (isReplaying()) {
            ByteArray src = (ByteArray) replay();
            if (src.length() != dst.remaining()) {
                throw new IllegalStateException(
                        "mismatching request: dst.remaining() has been changed since rewind.");
            }
            src.get(0, dst);
        } else if (index < super.firstIndex()) {
            throw new NoSuchElementException();
        } else if (index + dst.remaining() > super.endIndex()) {
            rewind();
        } else {
            ByteArray src = new HeapByteArray(dst.remaining());
            super.get(index, src, 0, dst.remaining());
            src.get(0, dst);
            record(src);
        }
    }

    @Override
    public byte get8(int index) {
        if (isReplaying()) {
            return ((Byte) replay()).byteValue();
        } else if (index >= super.endIndex()) {
            rewind();
            return 0;
        } else {
            return record(Byte.valueOf(super.get8(index)));
        }
    }

    @Override
    public short getBE16(int index) {
        if (isReplaying()) {
            return ((Short) replay()).shortValue();
        } else if (index + 2 > super.endIndex()) {
            rewind();
            return 0;
        } else {
            return record(Short.valueOf(super.getBE16(index)));
        }
    }

    @Override
    public int getBE24(int index) {
        if (isReplaying()) {
            return ((Integer) replay()).intValue();
        } else if (index + 3 > super.endIndex()) {
            rewind();
            return 0;
        } else {
            return record(Integer.valueOf(super.getBE24(index)));
        }
    }

    @Override
    public int getBE32(int index) {
        if (isReplaying()) {
            return ((Integer) replay()).intValue();
        } else if (index + 4 > super.endIndex()) {
            rewind();
            return 0;
        } else {
            return record(Integer.valueOf(super.getBE32(index)));
        }
    }

    @Override
    public long getBE48(int index) {
        if (isReplaying()) {
            return ((Long) replay()).longValue();
        } else if (index + 6 > super.endIndex()) {
            rewind();
            return 0;
        } else {
            return record(Long.valueOf(super.getBE48(index)));
        }
    }

    @Override
    public long getBE64(int index) {
        if (isReplaying()) {
            return ((Long) replay()).longValue();
        } else if (index + 8 > super.endIndex()) {
            rewind();
            return 0;
        } else {
            return record(Long.valueOf(super.getBE64(index)));
        }
    }


    @Override
    public short getLE16(int index) {
        if (isReplaying()) {
            return ((Short) replay()).shortValue();
        } else if (index + 2 > super.endIndex()) {
            rewind();
            return 0;
        } else {
            return record(Short.valueOf(super.getLE16(index)));
        }
    }

    @Override
    public int getLE24(int index) {
        if (isReplaying()) {
            return ((Integer) replay()).intValue();
        } else if (index + 3 > super.endIndex()) {
            rewind();
            return 0;
        } else {
            return record(Integer.valueOf(super.getLE24(index)));
        }
    }

    @Override
    public int getLE32(int index) {
        if (isReplaying()) {
            return ((Integer) replay()).intValue();
        } else if (index + 4 >= super.endIndex()) {
            rewind();
            return 0;
        } else {
            return record(Integer.valueOf(super.getLE32(index)));
        }
    }

    @Override
    public long getLE48(int index) {
        if (isReplaying()) {
            return ((Long) replay()).longValue();
        } else if (index + 6 > super.endIndex()) {
            rewind();
            return 0;
        } else {
            return record(Long.valueOf(super.getLE48(index)));
        }
    }

    @Override
    public long getLE64(int index) {
        if (isReplaying()) {
            return ((Long) replay()).longValue();
        } else if (index + 8 > super.endIndex()) {
            rewind();
            return 0;
        } else {
            return record(Long.valueOf(super.getLE64(index)));
        }
    }

    @Override
    public int compareTo(ByteArray o) {
        rejectUnreplayableOperation();
        return 0;
    }

    @Override
    public void copyTo(OutputStream out, int index, int length)
            throws IOException {
        rejectUnreplayableOperation();
    }

    @Override
    public void copyTo(OutputStream out) throws IOException {
        rejectUnreplayableOperation();
    }

    @Override
    public int copyTo(WritableByteChannel out, int index, int length)
            throws IOException {
        rejectUnreplayableOperation();
        return 0;
    }

    @Override
    public int copyTo(WritableByteChannel out) throws IOException {
        rejectUnreplayableOperation();
        return 0;
    }

    @Override
    public int copyTo(GatheringByteChannel out, int index, int length)
            throws IOException {
        rejectUnreplayableOperation();
        return 0;
    }

    @Override
    public int copyTo(GatheringByteChannel out) throws IOException {
        rejectUnreplayableOperation();
        return 0;
    }

    @Override
    public ByteBuffer getByteBuffer() {
        rejectUnreplayableOperation();
        return null;
    }

    @Override
    public ByteBuffer getByteBuffer(int index, int length) {
        rejectUnreplayableOperation();
        return null;
    }

    @Override
    public boolean empty() {
        return length() == 0;
    }

    @Override
    public int firstIndex() {
        return firstIndex;
    }

    @Override
    public long indexOf(int fromIndex, byte value) {
        rejectUnreplayableOperation();
        return NOT_FOUND;
    }

    @Override
    public long indexOf(int fromIndex, ByteArrayIndexFinder indexFinder) {
        rejectUnreplayableOperation();
        return NOT_FOUND;
    }

    @Override
    public long lastIndexOf(int fromIndex, byte value) {
        rejectUnreplayableOperation();
        return NOT_FOUND;
    }

    @Override
    public long lastIndexOf(int fromIndex, ByteArrayIndexFinder indexFinder) {
        rejectUnreplayableOperation();
        return NOT_FOUND;
    }

    @Override
    public int length() {
        return endIndex() - firstIndex();
    }

    public Iterator<ByteArray> iterator() {
        rejectUnreplayableOperation();
        return null;
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    private void rejectUnreplayableOperation() {
        throw new UnsupportedOperationException("unreplayable operation");
    }
}
