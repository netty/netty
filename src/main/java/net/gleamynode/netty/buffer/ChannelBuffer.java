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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * A random and sequential accessible sequence of zero or more bytes (octets).
 * This interface provides an abstract view for one or more primitive byte
 * arrays ({@code byte[]}) and {@linkplain ByteBuffer NIO buffers}.
 *
 * <h3>Creation of a buffer</h3>
 *
 * It is common for a user to create a new buffer using {@link ChannelBuffers}
 * utility class rather than calling an individual implementation's constructor.
 *
 * <h3>Random Access Indexing</h3>
 *
 * Just like an ordinary primitive byte array, {@link ChannelBuffer} uses
 * <a href="http://en.wikipedia.org/wiki/Index_(information_technology)#Array_element_identifier">zero-based indexing</a>.
 * It means the index of the first byte is always {@code 0} and the index of
 * the last byte is always {@link #capacity() capacity - 1}.  For example, to
 * iterate all bytes of a buffer, you can do the following, regardless of
 * its internal implementation:
 *
 * <pre>
 * ChannelBuffer buffer = ...;
 * for (int i = 0; i &lt; buffer.capacity(); i ++</strong>) {
 *     byte b = array.getByte(i);
 *     System.out.println((char) b);
 * }
 * </pre>
 *
 * <h3>Sequential Access Indexing</h3>
 *
 * {@link ChannelBuffer} provides two pointer variables to support sequential
 * read and write operations - {@link #readerIndex() readerIndex} for a read
 * operation and {@link #writerIndex() writerIndex} for a write operation
 * respectively.  The following diagram shows how a buffer is segmented into
 * three areas by the two pointers:
 *
 * <pre>
 *      +-------------------+------------------+------------------+
 *      | discardable bytes |  readable bytes  |  writable space  |
 *      +-------------------+------------------+------------------+
 *      |                   |                  |                  |
 *      0      <=      readerIndex   <=   writerIndex    <=    capacity
 * </pre>
 *
 * <h4>Readable bytes</h4>
 *
 * This segment is where the actual data is stored.  Any operation whose name
 * starts with {@code read} or {@code skip} will get or skip the data at the
 * current {@link #readerIndex() readerIndex} and increase it by the number of
 * read bytes.  If the argument of the read operation is also a
 * {@link ChannelBuffer} and no specific start index is specified, the
 * specified buffer's {@link #readerIndex() readerIndex} is increased together.
 * <p>
 * If there's no more readable bytes left, {@link IndexOutOfBoundsException}
 * is raised.  The default value of newly allocated, wrapped or copied buffer's
 * {@link #readerIndex() readerIndex} is {@code 0}.
 *
 * <pre>
 * // Iterates the readable bytes of a buffer.
 * ChannelBuffer buffer = ...;
 * while (buffer.isReadable()) {
 *     System.out.println(buffer.readByte());
 * }
 * </pre>
 *
 * <h4>Writable space</h4>
 *
 * This segment is undefined space which needs to be filled.  Any operation
 * whose name ends with {@code write} will write the data at the current
 * {@link #writerIndex() writerIndex} and increase it by the number of written
 * bytes.  If the argument of the write operation is also a {@link ChannelBuffer},
 * and no specific start index is specified, the specified buffer's
 * {@link #readerIndex() readerIndex} is increased together.
 * <p>
 * If there's no more writable space left, {@link IndexOutOfBoundsException}
 * is raised.  The default value of newly allocated buffer's
 * {@link #writerIndex() writerIndex} is {@code 0}.  The default value of
 * wrapped or copied buffer's {@link #writerIndex() writerIndex} is the
 * {@link #capacity() capacity} of the buffer.
 *
 * <pre>
 * // Fills the writable space of a buffer with random integers.
 * ChannelBuffer buffer = ...;
 * while (buffer.writableBytes() >= 4) {
 *     buffer.writeInt(random.nextInt());
 * }
 * </pre>
 *
 * <h4>Discardable bytes</h4>
 *
 * This segment contains the bytes which were read already by a read operation.
 * Initially, the size of this segment is {@code 0}, but its size increases up
 * to the {@link #writerIndex() writerIndex} as read operations are executed.
 * The read bytes can be discarded by calling {@link #discardReadBytes()} to
 * reclaim unused area as depicted by the following diagram:
 *
 * <pre>
 *  BEFORE discardReadBytes()
 *
 *      +-------------------+------------------+------------------+
 *      | discardable bytes |  readable bytes  |  writable space  |
 *      +-------------------+------------------+------------------+
 *      |                   |                  |                  |
 *      0      <=      readerIndex   <=   writerIndex    <=    capacity
 *
 *
 *  AFTER discardReadBytes()
 *
 *      +------------------+--------------------------------------+
 *      |  readable bytes  |    writable space (got more space)   |
 *      +------------------+--------------------------------------+
 *      |                  |                                      |
 * readerIndex (0) <= writerIndex (decreased)        <=        capacity
 * </pre>
 *
 * <h3>Search operations</h3>
 *
 * Various {@code indexOf()} methods help you locate an index of a value which
 * meets a certain criteria.  Complicated dynamic sequential search can be done
 * with {@link ChannelBufferIndexFinder} as well as simple static single byte
 * search.
 *
 * <h3>Conversion to a NIO buffer</h3>
 *
 * Various {@code toByteBuffer()} and {@code toByteBuffers()} methods convert
 * a {@link ChannelBuffer} into one or more NIO buffers.  These methods avoid
 * buffer allocation and memory copy whenever possible, but there's no
 * guarantee that memory copy will not be involved or that an explicit memory
 * copy will be involved.
 *
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public interface ChannelBuffer extends Comparable<ChannelBuffer> {

    /**
     * A buffer whose capacity is {@code 0}.
     */
    static ChannelBuffer EMPTY_BUFFER = new HeapChannelBuffer(0);

    /**
     * Returns the number of bytes (octets) this array can contain.
     */
    int capacity();

    int readerIndex();
    void readerIndex(int readerIndex);
    int writerIndex();
    void writerIndex(int writerIndex);

    int readableBytes();
    int writableBytes();
    boolean isReadable();
    boolean isWritable();

    void markReaderIndex();
    void resetReaderIndex();
    void markWriterIndex();
    void resetWriterIndex();

    void discardReadBytes();

    byte  getByte(int index);
    short getShort(int index);
    int   getMedium(int index);
    int   getInt(int index);
    long  getLong(int index);

    void  getBytes(int index, ChannelBuffer dst);
    void  getBytes(int index, ChannelBuffer dst, int dstIndex, int length);
    void  getBytes(int index, byte[] dst);
    void  getBytes(int index, byte[] dst, int dstIndex, int length);
    void  getBytes(int index, ByteBuffer dst);
    void  getBytes(int index, OutputStream out, int length) throws IOException;
    int   getBytes(int index, GatheringByteChannel out, int length) throws IOException;

    void setByte(int index, byte  value);
    void setShort(int index, short value);
    void setMedium(int index, int   value);
    void setInt(int index, int   value);
    void setLong(int index, long  value);

    void setBytes(int index, ChannelBuffer src);
    void setBytes(int index, ChannelBuffer src, int srcIndex, int length);
    void setBytes(int index, byte[] src);
    void setBytes(int index, byte[] src, int srcIndex, int length);
    void setBytes(int index, ByteBuffer src);
    void setBytes(int index, InputStream in, int length) throws IOException;
    int  setBytes(int index, ScatteringByteChannel in, int length) throws IOException;

    byte  readByte();
    short readShort();
    int   readMedium();
    int   readInt();
    long  readLong();

    ChannelBuffer readBytes();
    ChannelBuffer readBytes(int length);
    ChannelBuffer readBytes(ChannelBufferIndexFinder endIndexFinder);
    void readBytes(ChannelBuffer dst);
    void readBytes(ChannelBuffer dst, int length);
    void readBytes(ChannelBuffer dst, int dstIndex, int length);
    void readBytes(byte[] dst);
    void readBytes(byte[] dst, int dstIndex, int length);
    void readBytes(ByteBuffer dst);
    void readBytes(OutputStream out, int length) throws IOException;
    int  readBytes(GatheringByteChannel out, int length) throws IOException;

    void skipBytes(int length);
    int  skipBytes(ChannelBufferIndexFinder firstIndexFinder);

    void writeByte(byte  value);
    void writeShort(short value);
    void writeMedium(int   value);
    void writeInt(int   value);
    void writeLong(long  value);

    void writeBytes(ChannelBuffer src);
    void writeBytes(ChannelBuffer src, int length);
    void writeBytes(ChannelBuffer src, int srcIndex, int length);
    void writeBytes(byte[] src);
    void writeBytes(byte[] src, int srcIndex, int length);
    void writeBytes(ByteBuffer src);
    void writeBytes(InputStream in, int length) throws IOException;
    int  writeBytes(ScatteringByteChannel in, int length) throws IOException;

    void writePlaceholder(int length);

    int indexOf(int fromIndex, int toIndex, byte value);
    int indexOf(int fromIndex, int toIndex, ChannelBufferIndexFinder indexFinder);

    ChannelBuffer slice();
    ChannelBuffer slice(int index, int length);
    ChannelBuffer duplicate();

    ByteBuffer toByteBuffer();
    ByteBuffer toByteBuffer(int index, int length);
    ByteBuffer[] toByteBuffers();
    ByteBuffer[] toByteBuffers(int index, int length);

    /**
     * Returns a hash code which was calculated from the content of this array.
     * If there's a byte array which is {@linkplain #equals(Object) equal to}
     * this array, both arrays should return the same value.
     */
    int hashCode();

    /**
     * Determines if the content of the specified array is identical to the
     * content of this array.  'Identical' here means:
     * <ul>
     * <li>the length of the two arrays are same and</li>
     * <li>every single byte of the two arrays are same.</li>
     * </ul>
     * This method also returns {@code false} for {@code null} and an object
     * which is not an instance of {@link ChannelBuffer} type.
     */
    boolean equals(Object obj);

    /**
     * Compares the content of the specified array to the content of this
     * array.  Comparison is performed in the same manner with the string
     * comparison functions in various languages such as {@code strcmp},
     * {@code memcmp} and {@link String#compareTo(String)}.
     */
    int compareTo(ChannelBuffer buffer);

    /**
     * Returns the string representation of this array.  This method doesn't
     * necessarily return the whole content of the array.
     */
    String toString();
}
