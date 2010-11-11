/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.buffer;

import java.nio.ByteOrder;


/**
 * A little-endian Java heap buffer.  It is recommended to use {@link ChannelBuffers#buffer(ByteOrder, int)}
 * and {@link ChannelBuffers#wrappedBuffer(ByteOrder, byte[])} instead of
 * calling the constructor explicitly.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2206 $, $Date: 2010-03-03 14:35:01 +0900 (Wed, 03 Mar 2010) $
 */
public class LittleEndianHeapChannelBuffer extends HeapChannelBuffer {

    /**
     * Creates a new little-endian heap buffer with a newly allocated byte array.
     *
     * @param length the length of the new byte array
     */
    public LittleEndianHeapChannelBuffer(int length) {
        super(length);
    }

    /**
     * Creates a new little-endian heap buffer with an existing byte array.
     *
     * @param array the byte array to wrap
     */
    public LittleEndianHeapChannelBuffer(byte[] array) {
        super(array);
    }

    private LittleEndianHeapChannelBuffer(byte[] array, int readerIndex, int writerIndex) {
        super(array, readerIndex, writerIndex);
    }

    public ChannelBufferFactory factory() {
        return HeapChannelBufferFactory.getInstance(ByteOrder.LITTLE_ENDIAN);
    }

    public ByteOrder order() {
        return ByteOrder.LITTLE_ENDIAN;
    }

    public short getShort(int index) {
        return (short) (array[index] & 0xFF | array[index+1] << 8);
    }

    public int getUnsignedMedium(int index) {
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

    public void setShort(int index, int value) {
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
