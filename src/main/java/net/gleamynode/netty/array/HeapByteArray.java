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

/**
 *
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public class HeapByteArray extends AbstractByteArray {
    private final byte[] array;

    public HeapByteArray(int length) {
        array = new byte[length];
    }

    public HeapByteArray(byte[] array) {
        if (array == null) {
            throw new NullPointerException("array");
        }
        this.array = array;
    }

    public int firstIndex() {
        return 0;
    }

    public int length() {
        return array.length;
    }

    public byte get8(int index) {
        return array[index];
    }

    public short getBE16(int index) {
        return (short) (array[index] << 8 | array[index+1] & 0xFF);
    }

    public int getBE24(int index) {
        return  (array[index]   & 0xff) << 16 |
                (array[index+1] & 0xff) <<  8 |
                (array[index+2] & 0xff) <<  0;
    }

    public int getBE32(int index) {
        return  (array[index]   & 0xff) << 24 |
                (array[index+1] & 0xff) << 16 |
                (array[index+2] & 0xff) <<  8 |
                (array[index+3] & 0xff) <<  0;
    }

    public long getBE48(int index) {
        return  ((long) array[index]   & 0xff) << 40 |
                ((long) array[index+1] & 0xff) << 32 |
                ((long) array[index+2] & 0xff) << 24 |
                ((long) array[index+3] & 0xff) << 16 |
                ((long) array[index+4] & 0xff) <<  8 |
                ((long) array[index+5] & 0xff) <<  0;
    }

    public long getBE64(int index) {
        return  ((long) array[index]   & 0xff) << 56 |
                ((long) array[index+1] & 0xff) << 48 |
                ((long) array[index+2] & 0xff) << 40 |
                ((long) array[index+3] & 0xff) << 32 |
                ((long) array[index+4] & 0xff) << 24 |
                ((long) array[index+5] & 0xff) << 16 |
                ((long) array[index+6] & 0xff) <<  8 |
                ((long) array[index+7] & 0xff) <<  0;
    }

    public short getLE16(int index) {
        return (short) (array[index] & 0xFF | array[index+1] << 8);
    }

    public int getLE24(int index) {
        return  (array[index  ] & 0xff) <<  0 |
                (array[index+1] & 0xff) <<  8 |
                (array[index+2] & 0xff) << 16;
    }

    public int getLE32(int index) {
        return  (array[index  ] & 0xff) <<  0 |
                (array[index+1] & 0xff) <<  8 |
                (array[index+2] & 0xff) << 16 |
                (array[index+3] & 0xff) << 24;
    }

    public long getLE48(int index) {
        return  ((long) array[index]   & 0xff) <<  0 |
                ((long) array[index+1] & 0xff) <<  8 |
                ((long) array[index+2] & 0xff) << 16 |
                ((long) array[index+3] & 0xff) << 24 |
                ((long) array[index+4] & 0xff) << 32 |
                ((long) array[index+5] & 0xff) << 40;
    }

    public long getLE64(int index) {
        return  ((long) array[index]   & 0xff) <<  0 |
                ((long) array[index+1] & 0xff) <<  8 |
                ((long) array[index+2] & 0xff) << 16 |
                ((long) array[index+3] & 0xff) << 24 |
                ((long) array[index+4] & 0xff) << 32 |
                ((long) array[index+5] & 0xff) << 40 |
                ((long) array[index+6] & 0xff) << 48 |
                ((long) array[index+7] & 0xff) << 56;
    }

    public void get(int index, ByteArray dst, int dstIndex, int length) {
        if (dst instanceof HeapByteArray) {
            get(index, ((HeapByteArray) dst).array, dstIndex, length);
        } else {
            final int srcEndIndex = index + length;
            for (int i = index; i < srcEndIndex; i ++) {
                dst.set8(dstIndex ++, get8(i));
            }
        }
    }

    public void get(int index, byte[] dst, int dstIndex, int length) {
        System.arraycopy(array, index, dst, dstIndex, length);
    }

    public void get(int index, ByteBuffer dst) {
        dst.put(array, index, Math.min(length() - index, dst.remaining()));
    }

    public void set8(int index, byte value) {
        array[index] = value;
    }

    public void setBE16(int index, short value) {
        array[index  ] = (byte) (value >>> 8);
        array[index+1] = (byte) (value >>> 0);
    }

    public void setBE24(int index, int   value) {
        array[index  ] = (byte) (value >>> 16);
        array[index+1] = (byte) (value >>> 8);
        array[index+2] = (byte) (value >>> 0);
    }

    public void setBE32(int index, int   value) {
        array[index  ] = (byte) (value >>> 24);
        array[index+1] = (byte) (value >>> 16);
        array[index+2] = (byte) (value >>> 8);
        array[index+3] = (byte) (value >>> 0);
    }

    public void setBE48(int index, long  value) {
        array[index  ] = (byte) (value >>> 40);
        array[index+1] = (byte) (value >>> 32);
        array[index+2] = (byte) (value >>> 24);
        array[index+3] = (byte) (value >>> 16);
        array[index+4] = (byte) (value >>> 8);
        array[index+5] = (byte) (value >>> 0);
    }

    public void setBE64(int index, long  value) {
        array[index  ] = (byte) (value >>> 56);
        array[index+1] = (byte) (value >>> 48);
        array[index+2] = (byte) (value >>> 40);
        array[index+3] = (byte) (value >>> 32);
        array[index+4] = (byte) (value >>> 24);
        array[index+5] = (byte) (value >>> 16);
        array[index+6] = (byte) (value >>> 8);
        array[index+7] = (byte) (value >>> 0);
    }

    public void setLE16(int index, short value) {
        array[index  ] = (byte) (value >>> 0);
        array[index+1] = (byte) (value >>> 8);
    }

    public void setLE24(int index, int   value) {
        array[index  ] = (byte) (value >>> 0);
        array[index+1] = (byte) (value >>> 8);
        array[index+2] = (byte) (value >>> 16);
    }

    public void setLE32(int index, int   value) {
        array[index  ] = (byte) (value >>> 0);
        array[index+1] = (byte) (value >>> 8);
        array[index+2] = (byte) (value >>> 16);
        array[index+3] = (byte) (value >>> 24);
    }

    public void setLE48(int index, long  value) {
        array[index  ] = (byte) (value >>> 0);
        array[index+1] = (byte) (value >>> 8);
        array[index+2] = (byte) (value >>> 16);
        array[index+3] = (byte) (value >>> 24);
        array[index+4] = (byte) (value >>> 32);
        array[index+5] = (byte) (value >>> 40);
    }

    public void setLE64(int index, long  value) {
        array[index  ] = (byte) (value >>> 0);
        array[index+1] = (byte) (value >>> 8);
        array[index+2] = (byte) (value >>> 16);
        array[index+3] = (byte) (value >>> 24);
        array[index+4] = (byte) (value >>> 32);
        array[index+5] = (byte) (value >>> 40);
        array[index+6] = (byte) (value >>> 48);
        array[index+7] = (byte) (value >>> 56);
    }

    public void set(int index, ByteArray src, int srcIndex, int length) {
        if (src instanceof HeapByteArray) {
            set(index, ((HeapByteArray) src).array, srcIndex, length);
        } else {
            final int srcEndOffset = srcIndex + length;
            for (int i = srcIndex; i < srcEndOffset; i ++) {
                set8(index++, src.get8(i));
            }
        }
    }

    public void set(int index, byte[] src, int srcIndex, int length) {
        System.arraycopy(src, srcIndex, array, index, length);
    }

    public void set(int index, ByteBuffer src) {
        src.get(array, index, src.remaining());
    }

    public ByteBuffer[] toByteBuffers() {
        return new ByteBuffer[] { ByteBuffer.wrap(array) };
    }

    public void copyTo(OutputStream out, int index, int length)
            throws IOException {
        out.write(array, index, length);
    }

    public int copyTo(WritableByteChannel out, int index, int length)
            throws IOException {
        return out.write(ByteBuffer.wrap(array, index, length));
    }

    @Override
    public ByteBuffer getByteBuffer() {
        return ByteBuffer.wrap(array);
    }

    public ByteBuffer getByteBuffer(int index, int length) {
        return ByteBuffer.wrap(array, index, length);
    }
}
