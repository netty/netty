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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
 *      |                   |     (CONTENT)    |                  |
 *      +-------------------+------------------+------------------+
 *      |                   |                  |                  |
 *      0      <=      readerIndex   <=   writerIndex    <=    capacity
 * </pre>
 *
 * <h4>Readable bytes (the actual 'content' of the buffer)</h4>
 *
 * This segment, so called 'the <strong>content</strong> of a buffer', is where
 * the actual data is stored.  Any operation whose name starts with
 * {@code read} or {@code skip} will get or skip the data at the current
 * {@link #readerIndex() readerIndex} and increase it by the number of read
 * bytes.  If the argument of the read operation is also a {@link ChannelBuffer}
 * and no start index is specified, the specified buffer's
 * {@link #readerIndex() readerIndex} is increased together.
 * <p>
 * If there's not enough content left, {@link IndexOutOfBoundsException} is
 * raised.  The default value of newly allocated, wrapped or copied buffer's
 * {@link #readerIndex() readerIndex} is {@code 0}.
 *
 * <pre>
 * // Iterates the readable bytes of a buffer.
 * ChannelBuffer buffer = ...;
 * while (buffer.readable()) {
 *     System.out.println(buffer.readByte());
 * }
 * </pre>
 *
 * <h4>Writable space</h4>
 *
 * This segment is a undefined space which needs to be filled.  Any operation
 * whose name ends with {@code write} will write the data at the current
 * {@link #writerIndex() writerIndex} and increase it by the number of written
 * bytes.  If the argument of the write operation is also a {@link ChannelBuffer},
 * and no start index is specified, the specified buffer's
 * {@link #readerIndex() readerIndex} is increased together.
 * <p>
 * If there's not enough writable space left, {@link IndexOutOfBoundsException}
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
 *      |                   |     (CONTENT)    |                  |
 *      +-------------------+------------------+------------------+
 *      |                   |                  |                  |
 *      0      <=      readerIndex   <=   writerIndex    <=    capacity
 *
 *
 *  AFTER discardReadBytes()
 *
 *      +------------------+--------------------------------------+
 *      |  readable bytes  |    writable space (got more space)   |
 *      |     (CONTENT)    |                                      |
 *      +------------------+--------------------------------------+
 *      |                  |                                      |
 * readerIndex (0) <= writerIndex (decreased)        <=        capacity
 * </pre>
 *
 * <h4>Clearing the buffer indexes</h4>
 *
 * You can set both {@link #readerIndex() readerIndex} and
 * {@link #writerIndex() writerIndex} to {@code 0} by calling {@link #clear()}.
 * It doesn't clear the buffer content (e.g. filling with {@code 0}) but just
 * clears the two pointers.  Please also note that the semantic of this
 * operation is different from {@link ByteBuffer#clear()}.
 *
 * <pre>
 *  BEFORE clear()
 *
 *      +-------------------+------------------+------------------+
 *      | discardable bytes |  readable bytes  |  writable space  |
 *      |                   |     (CONTENT)    |                  |
 *      +-------------------+------------------+------------------+
 *      |                   |                  |                  |
 *      0      <=      readerIndex   <=   writerIndex    <=    capacity
 *
 *
 *  AFTER clear()
 *
 *      +---------------------------------------------------------+
 *      |             writable space (got more space)             |
 *      +---------------------------------------------------------+
 *      |                                                         |
 *      0 = readerIndex = writerIndex            <=            capacity
 * </pre>
 *
 * <h3>Search operations</h3>
 *
 * Various {@code indexOf()} methods help you locate an index of a value which
 * meets a certain criteria.  Complicated dynamic sequential search can be done
 * with {@link ChannelBufferIndexFinder} as well as simple static single byte
 * search.
 *
 * <h3>Mark and reset</h3>
 *
 * There are two marker indexes in every buffer. One is for storing
 * {@link #readerIndex() readerIndex} and the other is for storing
 * {@link #writerIndex() writerIndex}.  You can always reposition one of the
 * two indexes by calling a reset method.  It works in a similar fashion to
 * the mark and reset methods in {@link InputStream} except that there's no
 * {@code readlimit}.
 *
 * <h3>Derived buffers</h3>
 *
 * You can create a view of an existing buffer by calling either
 * {@link #duplicate()}, {@link #slice()} or {@link #slice(int, int)}.
 * A derived buffer will have an independent {@link #readerIndex() readerIndex},
 * {@link #writerIndex() writerIndex} and marker indexes, while it shares
 * other internal data representation, just like a NIO {@link ByteBuffer} does.
 * <p>
 * In case a completely fresh copy of an existing buffer is required, please
 * call {@link #copy()} method instead.
 *
 * <h3>Conversion to existing JDK types</h3>
 *
 * Various {@link #toByteBuffer()} and {@link #toByteBuffers()} methods convert
 * a {@link ChannelBuffer} into one or more NIO buffers.  These methods avoid
 * buffer allocation and memory copy whenever possible, but there's no
 * guarantee that memory copy will not be involved or that an explicit memory
 * copy will be involved.
 * <p>
 * In case you need to convert a {@link ChannelBuffer} into
 * an {@link InputStream} or an {@link OutputStream}, please refer to
 * {@link ChannelBufferInputStream} and {@link ChannelBufferOutputStream}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public interface ChannelBuffer extends Comparable<ChannelBuffer> {

    /**
     * A buffer whose capacity is {@code 0}.
     */
    static ChannelBuffer EMPTY_BUFFER = new BigEndianHeapChannelBuffer(0);

    /**
     * Returns the number of bytes (octets) this buffer can contain.
     */
    int capacity();

    /**
     * Returns the <a href="http://en.wikipedia.org/wiki/Endianness">endianness</a>
     * of this buffer.
     */
    ByteOrder order();

    /**
     * Returns the {@code readerIndex} of this buffer.
     */
    int readerIndex();

    /**
     * Sets the {@code readerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code readerIndex} is less than 0 or
     *         greater than {@code writerIndex}
     */
    void readerIndex(int readerIndex);

    /**
     * Returns the {@code writerIndex} of this buffer.
     */
    int writerIndex();

    /**
     * Sets the {@code writerIndex} of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code writerIndex} is less than
     *         {@code readerIndex} or greater than {@code capacity}
     */
    void writerIndex(int writerIndex);

    /**
     * Sets the {@code readerIndex} and {@code writerIndex} of this buffer
     * in one shot.  This method is useful because you don't need to worry
     * about the invocation order of {@link #readerIndex(int)} and {@link #writerIndex(int)}
     * methods.  For example, the following code will fail:
     *
     * <pre>
     * // Create a buffer whose readerIndex, writerIndex and capacity are
     * // 0, 0 and 8 respectively.
     * ChannelBuffer buf = ChannelBuffers.buffer(8);
     *
     * // IndexOutOfBoundsException is thrown because the specified
     * // readerIndex (2) cannot be greater than the current writerIndex (0).
     * buf.readerIndex(2);
     * buf.writerIndex(4);
     * </pre>
     *
     * The following code will also fail:
     *
     * <pre>
     * // Create a buffer whose readerIndex, writerIndex and capacity are
     * // 0, 8 and 8 respectively.
     * ChannelBuffer buf = ChannelBuffers.wrappedBuffer(new byte[8]);
     *
     * // readerIndex becomes 8.
     * buf.readLong();
     *
     * // IndexOutOfBoundsException is thrown because the specified
     * // writerIndex (4) cannot be less than the current readerIndex (8).
     * buf.writerIndex(4);
     * buf.readerIndex(2);
     * </pre>
     *
     * By contrast, {@link #setIndex(int, int)} guarantees that it never
     * throws an {@link IndexOutOfBoundsException} as long as the specified
     * indexes meets all constraints, regardless what the current index values
     * of the buffer are:
     *
     * <pre>
     * // No matter what the current state of the buffer is, the following
     * // call always succeeds as long as the capacity of the buffer is not
     * // less than 4.
     * buf.setIndex(2, 4);
     * </pre>
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code readerIndex} is less than 0,
     *         if the specified {@code writerIndex} is less than the specified
     *         {@code readerIndex} or if the specified {@code writerIndex} is
     *         greater than {@code capacity}
     */
    void setIndex(int readerIndex, int writerIndex);

    /**
     * Returns the number of readable bytes which equals to
     * {@code (writerIndex - readerIndex)}.
     */
    int readableBytes();

    /**
     * Returns the number of writable bytes which equals to
     * {@code (capacity - writerIndex)}.
     */
    int writableBytes();

    /**
     * Returns {@code true} if and only if {@link #readableBytes() readableBytes}
     * if greater than {@code 0}.
     */
    boolean readable();

    /**
     * Returns {@code true} if and only if {@link #writableBytes() writableBytes}
     * if greater than {@code 0}.
     */
    boolean writable();

    /**
     * Sets the {@code readerIndex} and {@code writerIndex} of this buffer to
     * {@code 0}.
     * This method is identical to {@link #setIndex(int, int) setIndex(0, 0)}.
     * <p>
     * Please note that the behavior of this method is different
     * from that of NIO {@link ByteBuffer}, which sets the {@code limit} to
     * the {@code capacity}.
     */
    void clear();

    /**
     * Marks the current {@code readerIndex} in this buffer.  You can restore
     * the marked {@code readerIndex} by calling {@link #resetReaderIndex()}.
     * The initial value of the marked {@code readerIndex} is always {@code 0}.
     */
    void markReaderIndex();

    /**
     * Repositions the current {@code readerIndex} to the marked {@code readerIndex}
     * in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the current {@code writerIndex} is less than the marked
     *         {@code readerIndex}
     */
    void resetReaderIndex();

    /**
     * Marks the current {@code writerIndex} in this buffer.  You can restore
     * the marked {@code writerIndex} by calling {@link #resetWriterIndex()}.
     * The initial value of the marked {@code writerIndex} is always {@code 0}.
     */
    void markWriterIndex();

    /**
     * Repositions the current {@code writerIndex} to the marked {@code writerIndex}
     * in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the current {@code readerIndex} is greater than the marked
     *         {@code writerIndex}
     */
    void resetWriterIndex();

    /**
     * Discards the bytes between the 0th index and {@code readerIndex}.
     * It moves the bytes between {@code readerIndex} and {@code writerIndex}
     * to the 0th index, and sets {@code readerIndex} and {@code writerIndex}
     * to {@code 0} and {@code oldWriterIndex - oldReaderIndex} respectively.
     * <p>
     * Please refer to the class documentation for more detailed explanation
     * with a diagram.
     */
    void discardReadBytes();

    /**
     * Gets a byte at the specified absolute {@code index} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code capacity}
     */
    byte  getByte(int index);

    /**
     * Gets a unsigned byte at the specified absolute {@code index} in this
     * buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code capacity}
     */
    short getUnsignedByte(int index);

    /**
     * Gets a 16-bit short integer at the specified absolute {@code index} in
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code capacity}
     */
    short getShort(int index);

    /**
     * Gets a unsigned 16-bit short integer at the specified absolute
     * {@code index} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code capacity}
     */
    int getUnsignedShort(int index);

    /**
     * Gets a 24-bit medium integer at the specified absolute {@code index} in
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code capacity}
     */
    int   getMedium(int index);

    /**
     * Gets a unsigned 24-bit medium integer at the specified absolute
     * {@code index} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code capacity}
     */
    int   getUnsignedMedium(int index);

    /**
     * Gets a 32-bit integer at the specified absolute {@code index} in
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code capacity}
     */
    int   getInt(int index);

    /**
     * Gets a unsigned 32-bit integer at the specified absolute {@code index}
     * in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code capacity}
     */
    long  getUnsignedInt(int index);

    /**
     * Gets a 64-bit long integer at the specified absolute {@code index} in
     * this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code capacity}
     */
    long  getLong(int index);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index} until the destination becomes
     * unwritable.  This method is basically same with {@link #getBytes(int, ChannelBuffer, int, int)},
     * except that this method advances the {@code writerIndex} of the
     * destination while {@link #getBytes(int, ChannelBuffer, int, int)}
     * doesn't.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + dst.writableBytes} is greater than {@code capacity}
     */
    void  getBytes(int index, ChannelBuffer dst);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index}.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if the specified {@code dstIndex} is less than {@code 0},
     *         if {@code index + length} is greater than {@code capacity}, or
     *         if {@code dstIndex + length} is greater than {@code dst.capacity}
     */
    void  getBytes(int index, ChannelBuffer dst, int dstIndex, int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index}.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + dst.length} is greater than {@code capacity}
     */
    void  getBytes(int index, byte[] dst);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index}.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if the specified {@code dstIndex} is less than {@code 0},
     *         if {@code index + length} is greater than {@code capacity}, or
     *         if {@code dstIndex + length} is greater than {@code dst.lenggth}
     */
    void  getBytes(int index, byte[] dst, int dstIndex, int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index} until the destination's position
     * reaches to its limit.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + dst.remaining()} is greater than {@code capacity}
     */
    void  getBytes(int index, ByteBuffer dst);

    /**
     * Transfers this buffer's data to the specified stream starting at the
     * specified absolute {@code index}.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than {@code capacity}
     * @throws IOException
     *         if the specified stream threw an exception during I/O
     */
    void  getBytes(int index, OutputStream out, int length) throws IOException;

    /**
     * Transfers this buffer's data to the specified channel starting at the
     * specified absolute {@code index}.
     *
     * @return the number of bytes written out to the specified channel
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + length} is greater than {@code capacity}
     * @throws IOException
     *         if the specified channel threw an exception during I/O
     */
    int   getBytes(int index, GatheringByteChannel out, int length) throws IOException;

    /**
     * Sets the specified byte at the specified absolute {@code index} in this
     * buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code capacity}
     */
    void setByte(int index, byte  value);

    /**
     * Sets the specified 16-bit short integer at the specified absolute
     * {@code index} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code capacity}
     */
    void setShort(int index, short value);

    /**
     * Sets the specified 24-bit medium integer at the specified absolute
     * {@code index} in this buffer.  Please note that the most significant
     * byte is ignored in the specified value.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code capacity}
     */
    void setMedium(int index, int   value);

    /**
     * Sets the specified 32-bit integer at the specified absolute
     * {@code index} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code capacity}
     */
    void setInt(int index, int   value);

    /**
     * Sets the specified 64-bit long integer at the specified absolute
     * {@code index} in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code capacity}
     */
    void setLong(int index, long  value);

    void setBytes(int index, ChannelBuffer src);
    void setBytes(int index, ChannelBuffer src, int srcIndex, int length);
    void setBytes(int index, byte[] src);
    void setBytes(int index, byte[] src, int srcIndex, int length);
    void setBytes(int index, ByteBuffer src);
    void setBytes(int index, InputStream in, int length) throws IOException;
    int  setBytes(int index, ScatteringByteChannel in, int length) throws IOException;

    void setZero(int index, int length);

    byte  readByte();
    short readUnsignedByte();
    short readShort();
    int   readUnsignedShort();
    int   readMedium();
    int   readUnsignedMedium();
    int   readInt();
    long  readUnsignedInt();
    long  readLong();

    ChannelBuffer readBytes(int length);
    ChannelBuffer readBytes(ChannelBufferIndexFinder endIndexFinder);
    ChannelBuffer readSlice(int length);
    ChannelBuffer readSlice(ChannelBufferIndexFinder endIndexFinder);
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

    void writeZero(int length);

    int indexOf(int fromIndex, int toIndex, byte value);
    int indexOf(int fromIndex, int toIndex, ChannelBufferIndexFinder indexFinder);

    ChannelBuffer copy();
    ChannelBuffer copy(int index, int length);
    ChannelBuffer slice();
    ChannelBuffer slice(int index, int length);
    ChannelBuffer duplicate();

    ByteBuffer toByteBuffer();
    ByteBuffer toByteBuffer(int index, int length);
    ByteBuffer[] toByteBuffers();
    ByteBuffer[] toByteBuffers(int index, int length);

    String toString(String charsetName);
    String toString(String charsetName, ChannelBufferIndexFinder terminatorFinder);
    String toString(int index, int length, String charsetName);
    String toString(int index, int length, String charsetName, ChannelBufferIndexFinder terminatorFinder);

    /**
     * Returns a hash code which was calculated from the content of this
     * buffer.  If there's a byte array which is
     * {@linkplain #equals(Object) equal to} this array, both arrays should
     * return the same value.
     */
    int hashCode();

    /**
     * Determines if the content of the specified buffer is identical to the
     * content of this array.  'Identical' here means:
     * <ul>
     * <li>the size of the contents of the two buffers are same and</li>
     * <li>every single byte of the content of the two buffers are same.</li>
     * </ul>
     * Please note that it doesn't compare {@link #readerIndex()} nor
     * {@link #writerIndex()}.  This method also returns {@code false} for
     * {@code null} and an object which is not an instance of
     * {@link ChannelBuffer} type.
     */
    boolean equals(Object obj);

    /**
     * Compares the content of the specified buffer to the content of this
     * buffer.  Comparison is performed in the same manner with the string
     * comparison functions of various languages such as {@code strcmp},
     * {@code memcmp} and {@link String#compareTo(String)}.
     */
    int compareTo(ChannelBuffer buffer);

    /**
     * Returns the string representation of this buffer.  This method doesn't
     * necessarily return the whole content of the buffer but returns
     * the values of the key properties such as {@link #readerIndex()},
     * {@link #writerIndex()} and {@link #capacity()}..
     */
    String toString();
}
