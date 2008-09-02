/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.buffer;

import java.nio.ByteOrder;


/**
 * A big-endian Java heap buffer.  It is recommended to use {@link ChannelBuffers#buffer(int)}
 * and {@link ChannelBuffers#wrappedBuffer(byte[])} instead of calling the
 * constructor explicitly.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public class BigEndianHeapChannelBuffer extends HeapChannelBuffer {

    /**
     * Creates a new big-endian heap buffer with a newly allocated byte array.
     *
     * @param length the length of the new byte array
     */
    public BigEndianHeapChannelBuffer(int length) {
        super(length);
    }

    /**
     * Creates a new big-endian heap buffer with an existing byte array.
     *
     * @param array the byte array to wrap
     */
    public BigEndianHeapChannelBuffer(byte[] array) {
        super(array);
    }

    private BigEndianHeapChannelBuffer(byte[] array, int readerIndex, int writerIndex) {
        super(array, readerIndex, writerIndex);
    }

    public ByteOrder order() {
        return ByteOrder.BIG_ENDIAN;
    }

    public short getShort(int index) {
        return (short) (array[index] << 8 | array[index+1] & 0xFF);
    }

    public int getUnsignedMedium(int index) {
        return  (array[index]   & 0xff) << 16 |
                (array[index+1] & 0xff) <<  8 |
                (array[index+2] & 0xff) <<  0;
    }

    public int getInt(int index) {
        return  (array[index]   & 0xff) << 24 |
                (array[index+1] & 0xff) << 16 |
                (array[index+2] & 0xff) <<  8 |
                (array[index+3] & 0xff) <<  0;
    }

    public long getLong(int index) {
        return  ((long) array[index]   & 0xff) << 56 |
                ((long) array[index+1] & 0xff) << 48 |
                ((long) array[index+2] & 0xff) << 40 |
                ((long) array[index+3] & 0xff) << 32 |
                ((long) array[index+4] & 0xff) << 24 |
                ((long) array[index+5] & 0xff) << 16 |
                ((long) array[index+6] & 0xff) <<  8 |
                ((long) array[index+7] & 0xff) <<  0;
    }

    public void setShort(int index, short value) {
        array[index  ] = (byte) (value >>> 8);
        array[index+1] = (byte) (value >>> 0);
    }

    public void setMedium(int index, int   value) {
        array[index  ] = (byte) (value >>> 16);
        array[index+1] = (byte) (value >>> 8);
        array[index+2] = (byte) (value >>> 0);
    }

    public void setInt(int index, int   value) {
        array[index  ] = (byte) (value >>> 24);
        array[index+1] = (byte) (value >>> 16);
        array[index+2] = (byte) (value >>> 8);
        array[index+3] = (byte) (value >>> 0);
    }

    public void setLong(int index, long  value) {
        array[index  ] = (byte) (value >>> 56);
        array[index+1] = (byte) (value >>> 48);
        array[index+2] = (byte) (value >>> 40);
        array[index+3] = (byte) (value >>> 32);
        array[index+4] = (byte) (value >>> 24);
        array[index+5] = (byte) (value >>> 16);
        array[index+6] = (byte) (value >>> 8);
        array[index+7] = (byte) (value >>> 0);
    }

    public ChannelBuffer duplicate() {
        return new BigEndianHeapChannelBuffer(array, readerIndex(), writerIndex());
    }

    public ChannelBuffer copy(int index, int length) {
        if (index < 0 || length < 0 || index + length > array.length) {
            throw new IndexOutOfBoundsException();
        }

        byte[] copiedArray = new byte[length];
        System.arraycopy(array, index, copiedArray, 0, length);
        return new BigEndianHeapChannelBuffer(copiedArray);
    }
}
