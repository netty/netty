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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public abstract class HeapChannelBuffer extends AbstractChannelBuffer {

    protected final byte[] array;

    public HeapChannelBuffer(int length) {
        this(new byte[length], 0, 0);
    }

    public HeapChannelBuffer(byte[] array) {
        this(array, 0, array.length);
    }

    protected HeapChannelBuffer(byte[] array, int readerIndex, int writerIndex) {
        if (array == null) {
            throw new NullPointerException("array");
        }
        this.array = array;
        setIndex(readerIndex, writerIndex);
    }

    public int capacity() {
        return array.length;
    }

    public byte getByte(int index) {
        return array[index];
    }

    public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
        if (dst instanceof HeapChannelBuffer) {
            getBytes(index, ((HeapChannelBuffer) dst).array, dstIndex, length);
        } else {
            dst.setBytes(dstIndex, array, index, length);
        }
    }

    public void getBytes(int index, byte[] dst, int dstIndex, int length) {
        System.arraycopy(array, index, dst, dstIndex, length);
    }

    public void getBytes(int index, ByteBuffer dst) {
        dst.put(array, index, Math.min(capacity() - index, dst.remaining()));
    }

    public void getBytes(int index, OutputStream out, int length)
            throws IOException {
        out.write(array, index, length);
    }

    public int getBytes(int index, GatheringByteChannel out, int length)
            throws IOException {
        return out.write(ByteBuffer.wrap(array, index, length));
    }

    public void setByte(int index, byte value) {
        array[index] = value;
    }

    public void setBytes(int index, ChannelBuffer src, int srcIndex, int length) {
        if (src instanceof HeapChannelBuffer) {
            setBytes(index, ((HeapChannelBuffer) src).array, srcIndex, length);
        } else {
            src.getBytes(srcIndex, array, index, length);
        }
    }

    public void setBytes(int index, byte[] src, int srcIndex, int length) {
        System.arraycopy(src, srcIndex, array, index, length);
    }

    public void setBytes(int index, ByteBuffer src) {
        src.get(array, index, src.remaining());
    }

    public void setBytes(int index, InputStream in, int length) throws IOException {
        while (length > 0) {
            int readBytes = in.read(array, index, length);
            if (readBytes < 0) {
                throw new EOFException();
            }
            index += readBytes;
            length -= readBytes;
        }
    }

    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(array, index, length);
        while (length > 0) {
            int readBytes = in.read(buf);
            if (readBytes < 0) {
                throw new EOFException();
            } else if (readBytes == 0) {
                break;
            }
        }

        return buf.flip().remaining();
    }

    public ChannelBuffer slice(int index, int length) {
        if (index == 0) {
            if (length == array.length) {
                return duplicate();
            } else {
                return new TruncatedChannelBuffer(this, length);
            }
        } else {
            return new SlicedChannelBuffer(this, index, length);
        }
    }

    public ByteBuffer toByteBuffer(int index, int length) {
        return ByteBuffer.wrap(array, index, length);
    }
}
