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

import java.nio.ByteOrder;


/**
 *
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 */
public class LittleEndianHeapChannelBuffer extends HeapChannelBuffer {

    public LittleEndianHeapChannelBuffer(int length) {
        super(length);
    }

    public LittleEndianHeapChannelBuffer(byte[] array) {
        super(array);
    }

    private LittleEndianHeapChannelBuffer(byte[] array, int readerIndex, int writerIndex) {
        super(array, readerIndex, writerIndex);
    }

    public ByteOrder order() {
        return ByteOrder.LITTLE_ENDIAN;
    }

    public short getShort(int index) {
        return (short) (array[index] & 0xFF | array[index+1] << 8);
    }

    public int getMedium(int index) {
        return (array[index  ] & 0xff) <<  0 |
               (array[index+1] & 0xff) <<  8 |
               (array[index+2] & 0xff) << 16;
    }

    public int getInt(int index) {
        return (array[index  ] & 0xff) <<  0 |
               (array[index+1] & 0xff) <<  8 |
               (array[index+2] & 0xff) << 16 |
               (array[index+3] & 0xff) << 24;
    }

    public long getLong(int index) {
        return ((long) array[index]   & 0xff) <<  0 |
               ((long) array[index+1] & 0xff) <<  8 |
               ((long) array[index+2] & 0xff) << 16 |
               ((long) array[index+3] & 0xff) << 24 |
               ((long) array[index+4] & 0xff) << 32 |
               ((long) array[index+5] & 0xff) << 40 |
               ((long) array[index+6] & 0xff) << 48 |
               ((long) array[index+7] & 0xff) << 56;
    }

    public void setShort(int index, short value) {
        array[index  ] = (byte) (value >>> 0);
        array[index+1] = (byte) (value >>> 8);
    }

    public void setMedium(int index, int   value) {
        array[index  ] = (byte) (value >>> 0);
        array[index+1] = (byte) (value >>> 8);
        array[index+2] = (byte) (value >>> 16);
    }

    public void setInt(int index, int   value) {
        array[index  ] = (byte) (value >>> 0);
        array[index+1] = (byte) (value >>> 8);
        array[index+2] = (byte) (value >>> 16);
        array[index+3] = (byte) (value >>> 24);
    }

    public void setLong(int index, long  value) {
        array[index  ] = (byte) (value >>> 0);
        array[index+1] = (byte) (value >>> 8);
        array[index+2] = (byte) (value >>> 16);
        array[index+3] = (byte) (value >>> 24);
        array[index+4] = (byte) (value >>> 32);
        array[index+5] = (byte) (value >>> 40);
        array[index+6] = (byte) (value >>> 48);
        array[index+7] = (byte) (value >>> 56);
    }

    public ChannelBuffer duplicate() {
        return new LittleEndianHeapChannelBuffer(array, readerIndex(), writerIndex());
    }

    public ChannelBuffer copy(int index, int length) {
        if (index < 0 || length < 0 || index + length > array.length) {
            throw new IndexOutOfBoundsException();
        }

        byte[] copiedArray = new byte[length];
        System.arraycopy(array, index, copiedArray, 0, length);
        return new LittleEndianHeapChannelBuffer(copiedArray);
    }
}
