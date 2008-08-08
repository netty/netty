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
import java.nio.ByteOrder;
import java.nio.channels.WritableByteChannel;


public class ByteBufferBackedByteArray extends AbstractByteArray {
    private final ByteBuffer dataBE;
    private final ByteBuffer dataLE;
    private final int length;

    public ByteBufferBackedByteArray(ByteBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }

        dataBE = buffer.duplicate().order(ByteOrder.BIG_ENDIAN);
        dataLE = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        length = dataBE.remaining();
    }

    public int firstIndex() {
        return dataBE.position();
    }

    @Override
    public int endIndex() {
        return dataBE.limit();
    }

    public int length() {
        return length;
    }

    public byte get8(int index) {
        return dataBE.get(index);
    }

    public short getBE16(int index) {
        return dataBE.getShort(index);
    }

    public int getBE24(int index) {
        return  (get8(index)   & 0xff) << 16 |
                (get8(index+1) & 0xff) <<  8 |
                (get8(index+2) & 0xff) <<  0;
    }

    public int getBE32(int index) {
        return dataBE.getInt(index);
    }

    public long getBE48(int index) {
        return  ((long) get8(index)   & 0xff) << 40 |
                ((long) get8(index+1) & 0xff) << 32 |
                ((long) get8(index+2) & 0xff) << 24 |
                ((long) get8(index+3) & 0xff) << 16 |
                ((long) get8(index+4) & 0xff) <<  8 |
                ((long) get8(index+5) & 0xff) <<  0;
    }

    public long getBE64(int index) {
        return dataBE.getLong(index);
    }

    public short getLE16(int index) {
        return dataLE.getShort(index);
    }

    public int getLE24(int index) {
        return  (get8(index)   & 0xff) <<  0 |
                (get8(index+1) & 0xff) <<  8 |
                (get8(index+2) & 0xff) << 16;
    }

    public int getLE32(int index) {
        return dataLE.getInt(index);
    }

    public long getLE48(int index) {
        return  ((long) get8(index)   & 0xff) <<  0 |
                ((long) get8(index+1) & 0xff) <<  8 |
                ((long) get8(index+2) & 0xff) << 16 |
                ((long) get8(index+3) & 0xff) << 24 |
                ((long) get8(index+4) & 0xff) << 32 |
                ((long) get8(index+5) & 0xff) << 40;
    }

    public long getLE64(int index) {
        return dataLE.getLong(index);
    }

    public void get(int index, ByteArray dst, int dstIndex, int length) {
        if (dst instanceof ByteBufferBackedByteArray) {
            ByteBuffer data = ((ByteBufferBackedByteArray) dst).dataBE.duplicate();
            data.position(dstIndex).limit(dstIndex + length);
            get(index, data);
        } else {
            final int srcEndIndex = index + length;
            for (int i = index; i < srcEndIndex; i ++) {
                dst.set8(dstIndex ++, get8(i));
            }
        }
    }

    public void get(int index, byte[] dst, int dstIndex, int length) {
        ByteBuffer data = dataBE.duplicate();
        data.position(index).limit(index + length);
        data.get(dst, dstIndex, length);
    }

    public void get(int index, ByteBuffer dst) {
        ByteBuffer data = dataBE.duplicate();
        data.position(index).limit(
                index + Math.min(length() - index, dst.remaining()));
        dst.put(data);
    }

    public void set8(int index, byte value) {
        dataBE.put(index, value);
    }

    public void setBE16(int index, short value) {
        dataBE.putShort(index, value);
    }

    public void setBE24(int index, int   value) {
        set8(index,   (byte) (value >>> 16));
        set8(index+1, (byte) (value >>>  8));
        set8(index+2, (byte) (value >>>  0));
    }

    public void setBE32(int index, int   value) {
        dataBE.putInt(index, value);
    }

    public void setBE48(int index, long  value) {
        set8(index  , (byte) (value >>> 40));
        set8(index+1, (byte) (value >>> 32));
        set8(index+2, (byte) (value >>> 24));
        set8(index+3, (byte) (value >>> 16));
        set8(index+4, (byte) (value >>>  8));
        set8(index+5, (byte) (value >>>  0));
    }

    public void setBE64(int index, long  value) {
        dataBE.putLong(index, value);
    }

    public void setLE16(int index, short value) {
        dataLE.putShort(index, value);
    }

    public void setLE24(int index, int   value) {
        set8(index  , (byte) (value >>>  0));
        set8(index+1, (byte) (value >>>  8));
        set8(index+2, (byte) (value >>> 16));
    }

    public void setLE32(int index, int   value) {
        dataLE.putInt(index, value);
    }

    public void setLE48(int index, long  value) {
        set8(index  , (byte) (value >>>  0));
        set8(index+1, (byte) (value >>>  8));
        set8(index+2, (byte) (value >>> 16));
        set8(index+3, (byte) (value >>> 24));
        set8(index+4, (byte) (value >>> 32));
        set8(index+5, (byte) (value >>> 40));
    }

    public void setLE64(int index, long  value) {
        dataLE.putLong(index, value);
    }

    public void set(int index, ByteArray src, int srcIndex, int length) {
        if (src instanceof ByteBufferBackedByteArray) {
            ByteBuffer data = ((ByteBufferBackedByteArray) src).dataBE.duplicate();
            data.position(srcIndex).limit(srcIndex + length);
            set(index, data);
        } else {
            final int srcEndIndex = srcIndex + length;
            for (int i = srcIndex; i < srcEndIndex; i ++) {
                set8(index++, src.get8(i));
            }
        }
    }

    public void set(int index, byte[] src, int srcIndex, int length) {
        ByteBuffer data = dataBE.duplicate();
        data.position(index).limit(index + length);
        data.put(src, srcIndex, length);
    }

    public void set(int index, ByteBuffer src) {
        ByteBuffer data = dataBE.duplicate();
        data.position(index).limit(index + src.remaining());
        data.put(src);
    }

    public ByteBuffer[] toByteBuffers() {
        return new ByteBuffer[] { dataBE.duplicate() };
    }

    public void copyTo(OutputStream out, int index, int length) throws IOException {
        if (index < firstIndex() ||
            length > length() || length < 0) {
            throw new IndexOutOfBoundsException();
        }

        if (length == 0) {
            return;
        }

        if (!dataBE.isReadOnly() && dataBE.hasArray()) {
            out.write(
                    dataBE.array(),
                    index + dataBE.arrayOffset(),
                    length);
        } else {
            byte[] tmp = new byte[length];
            ((ByteBuffer) dataBE.duplicate().position(index)).get(tmp);
            out.write(tmp);
        }
    }

    public int copyTo(WritableByteChannel out, int index, int length) throws IOException {
        if (index < firstIndex() ||
            length > length() || length < 0) {
            throw new IndexOutOfBoundsException();
        }

        if (length == 0) {
            return 0;
        }

        return out.write((ByteBuffer) dataBE.duplicate().position(index).limit(index + length));
    }

    @Override
    public ByteBuffer getByteBuffer() {
        return dataBE.duplicate();
    }

    public ByteBuffer getByteBuffer(int index, int length) {
        return (ByteBuffer) dataBE.duplicate().position(index).limit(index + length);
    }
}
