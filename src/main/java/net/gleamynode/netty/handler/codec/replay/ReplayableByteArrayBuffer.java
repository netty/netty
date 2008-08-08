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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.array.ByteArrayIndexFinder;
import net.gleamynode.netty.array.CompositeByteArray;
import net.gleamynode.netty.array.ReadOnlyByteArrayBuffer;


/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class ReplayableByteArrayBuffer extends ReadOnlyByteArrayBuffer implements Iterable<ByteArray> {
    private static final Object VOID = new Object();

    private final List<Object> returnValues = new ArrayList<Object>(128);
    private int currentIndex;

    public ReplayableByteArrayBuffer() {
        super(new CompositeByteArray());
    }

    public void rewind() {
        currentIndex = 0;
        throw REWIND;
    }

    public boolean isReplaying() {
        return currentIndex < returnValues.size();
    }

    public Object replay() {
        return returnValues.get(currentIndex ++);
    }

    public void replayVoid() {
        if (replay() != VOID) {
            throw new IllegalStateException("Expected VOID return value");
        }
    }

    public <T> T record(T returnValue) {
        returnValues.add(returnValue);
        currentIndex ++;
        return returnValue;
    }

    public void clear() {
        currentIndex = 0;
        returnValues.clear();
    }

    @Override
    public ByteArray read() {
        if (isReplaying()) {
            return (ByteArray) replay();
        } else if (empty()) {
            rewind();
            return null;
        } else {
            return record(super.read());
        }
    }

    @Override
    public ByteArray read(ByteArrayIndexFinder endIndexFinder) {
        if (isReplaying()) {
            return (ByteArray) replay();
        } else if (super.indexOf(0, endIndexFinder) == NOT_FOUND) {
            rewind();
            return null;
        } else {
            return record(super.read(endIndexFinder));
        }
    }

    @Override
    public ByteArray read(int length) {
        if (isReplaying()) {
            return (ByteArray) replay();
        } else if (length > length()) {
            rewind();
            return null;
        } else {
            return record(super.read(length));
        }
    }

    @Override
    public byte read8() {
        if (isReplaying()) {
            return ((Byte) replay()).byteValue();
        } else if (empty()) {
            rewind();
            return 0;
        } else {
            return record(Byte.valueOf(super.read8()));
        }
    }

    @Override
    public short readBE16() {
        if (isReplaying()) {
            return ((Short) replay()).shortValue();
        } else if (length() < 2) {
            rewind();
            return 0;
        } else {
            return record(Short.valueOf(super.readBE16()));
        }
    }

    @Override
    public int readBE24() {
        if (isReplaying()) {
            return ((Integer) replay()).intValue();
        } else if (length() < 3) {
            rewind();
            return 0;
        } else {
            return record(Integer.valueOf(super.readBE24()));
        }
    }

    @Override
    public int readBE32() {
        if (isReplaying()) {
            return ((Integer) replay()).intValue();
        } else if (length() < 4) {
            rewind();
            return 0;
        } else {
            return record(Integer.valueOf(super.readBE32()));
        }
    }

    @Override
    public long readBE48() {
        if (isReplaying()) {
            return ((Long) replay()).longValue();
        } else if (length() < 6) {
            rewind();
            return 0;
        } else {
            return record(Long.valueOf(super.readBE48()));
        }
    }

    @Override
    public long readBE64() {
        if (isReplaying()) {
            return ((Long) replay()).longValue();
        } else if (length() < 8) {
            rewind();
            return 0;
        } else {
            return record(Long.valueOf(super.readBE64()));
        }
    }

    @Override
    public short readLE16() {
        if (isReplaying()) {
            return ((Short) replay()).shortValue();
        } else if (length() < 2) {
            rewind();
            return 0;
        } else {
            return record(Short.valueOf(super.readLE16()));
        }
    }

    @Override
    public int readLE24() {
        if (isReplaying()) {
            return ((Integer) replay()).intValue();
        } else if (length() < 3) {
            rewind();
            return 0;
        } else {
            return record(Integer.valueOf(super.readLE24()));
        }
    }

    @Override
    public int readLE32() {
        if (isReplaying()) {
            return ((Integer) replay()).intValue();
        } else if (length() < 4) {
            rewind();
            return 0;
        } else {
            return record(Integer.valueOf(super.readLE32()));
        }
    }

    @Override
    public long readLE48() {
        if (isReplaying()) {
            return ((Long) replay()).longValue();
        } else if (length() < 6) {
            rewind();
            return 0;
        } else {
            return record(Long.valueOf(super.readLE48()));
        }
    }

    @Override
    public long readLE64() {
        if (isReplaying()) {
            return ((Long) replay()).longValue();
        } else if (length() < 8) {
            rewind();
            return 0;
        } else {
            return record(Long.valueOf(super.readLE64()));
        }
    }

    @Override
    public void skip(ByteArrayIndexFinder firstIndexFinder) {
        if (isReplaying()) {
            replayVoid();
        } else if (super.indexOf(0, firstIndexFinder) == NOT_FOUND) {
            rewind();
        } else {
            super.skip(firstIndexFinder);
            record(VOID);
        }
    }

    @Override
    public void skip(int length) {
        if (isReplaying()) {
            replayVoid();
        } else if (length > length()) {
            rewind();
        } else {
            super.skip(length);
            record(VOID);
        }
    }

    @Override
    public void get(int index, byte[] dst, int dstIndex, int length) {
        if (isReplaying()) {
            replayVoid();
        } else if (index < firstIndex() || dstIndex + length > dst.length) {
            throw new NoSuchElementException();
        } else if (index + length > endIndex()) {
            rewind();
        } else {
            super.get(index, dst, dstIndex, length);
            record(VOID);
        }
    }

    @Override
    public void get(int index, byte[] dst) {
        get(index, dst, 0, dst.length);
    }

    @Override
    public void get(int index, ByteArray dst, int dstIndex, int length) {
        if (isReplaying()) {
            replayVoid();
        } else if (index < firstIndex() || dstIndex + length > dst.endIndex()) {
            throw new NoSuchElementException();
        } else if (index + length > endIndex()) {
            rewind();
        } else {
            super.get(index, dst, dstIndex, length);
            record(VOID);
        }
    }

    @Override
    public void get(int index, ByteArray dst) {
        get(index, dst, dst.firstIndex(), dst.endIndex());
    }

    @Override
    public void get(int index, ByteBuffer dst) {
        if (isReplaying()) {
            replayVoid();
        } else if (index < firstIndex()) {
            throw new NoSuchElementException();
        } else if (index + dst.remaining() > endIndex()) {
            rewind();
        } else {
            super.get(index, dst);
            record(VOID);
        }
    }

    @Override
    public byte get8(int index) {
        if (isReplaying()) {
            return ((Byte) replay()).byteValue();
        } else if (index >= length()) {
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
        } else if (index + 2 > length()) {
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
        } else if (index + 3 > length()) {
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
        } else if (index + 4 > length()) {
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
        } else if (index + 6 > length()) {
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
        } else if (index + 8 > length()) {
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
        } else if (index + 2 > length()) {
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
        } else if (index + 3 > length()) {
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
        } else if (index + 4 > length()) {
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
        } else if (index + 6 > length()) {
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
        } else if (index + 8 > length()) {
            rewind();
            return 0;
        } else {
            return record(Long.valueOf(super.getLE64(index)));
        }
    }

    protected void rejectReplay() {
        throw new UnsupportedOperationException("replayable");
    }

    public Iterator<ByteArray> iterator() {
        return ((CompositeByteArray) unwrap()).iterator();
    }
}
