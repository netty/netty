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
import java.nio.channels.Channel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * A random-accessible sequence of zero or more bytes (octets).
 * This interface provides an abstract view for one or more primitive byte
 * arrays ({@code byte[]}) and {@linkplain ByteBuffer NIO buffers}.
 *
 * <h3>Index</h3>
 *
 * Unlike an ordinary primitive byte array, {@link ByteArray} allows a negative
 * index, which means its {@linkplain #firstIndex() first index} can be either
 * less than {@code 0}, equal to {@code 0} or even greater than {@code 0}.
 * Therefore, you should not make an assumption that an array's first index is
 * always {@code 0}.  For example, to iterate all bytes over an array one by
 * one, you have to do the following, regardless of the array's implementation
 * detail:
 * <pre>
 * ByteArray array = ...;
 * for (<strong>int i = array.firstIndex(); i &lt; array.endIndex(); i ++</strong>) {
 *     byte b = array.get8(i);
 *     System.out.println((char) b);
 * }
 * </pre>
 *
 * <h3>Getters and Setters</h3>
 *
 * Various getter and setter methods are provided, from a single byte access
 * method to big and little endian 16 / 24 / 32 / 48 / 64-bit integer access
 * methods.  Bulk access operations for primitive byte array, NIO buffer and
 * {@link ByteArray} are also available.
 *
 * <h3>Search operations</h3>
 *
 * <strong>{@code indexOf()}</strong> and <strong>{@code lastIndexOf()}</strong>
 * methods help you locate an index of a value which meets a certain criteria.
 * Complicated dynamic sequential search can be done with
 * {@link ByteArrayIndexFinder} as well as simple static single byte search.
 *
 * <h3>I/O operations</h3>
 *
 * <strong>{@code copyTo()}</strong> methods write the partial or entire
 * content of an array to an {@link OutputStream} or a NIO {@link Channel}.
 * Gathering write operation is performed whenever possible.
 *
 * <h3>Conversion to a NIO buffer</h3>
 *
 * <strong>{@code getByteBuffer()}</strong> methods return a NIO buffer which
 * is either a wrapper (using {@link ByteBuffer#wrap(byte[])}) or a deep copy
 * (creating a new NIO buffer) of the partial or entire content of an array.
 * These methods will always avoid buffer allocation and memory copy whenever
 * possible, but there's no guarantee that they will always return a wrapper.
 *
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public interface ByteArray extends Comparable<ByteArray> {

    /**
     * An array whose length is {@code 0}.
     */
    static ByteArray EMPTY_BUFFER = new HeapByteArray(0);

    /**
     * A value which is returned when a search operation failed because
     * no value was found.
     */
    static long NOT_FOUND = Long.MIN_VALUE;

    /**
     * Returns the first index of this array.
     */
    int firstIndex();

    /**
     * Returns the end index ({@code firstIndex() + length()}) of this array.
     */
    int endIndex();

    /**
     * Returns the number of bytes (octets) this array contains.
     */
    int length();

    /**
     * Returns {@code true} if and only if this array's
     * {@linkplain #length() length} is {@code 0}.
     */
    boolean empty();

    /**
     * Gets a byte value at the specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 1)
     */
    byte  get8   (int index);

    /**
     * Gets a 16-bit big endian integer at the specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 2)
     */
    short getBE16(int index);

    /**
     * Gets a 24-bit big endian integer at the specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 3)
     */
    int   getBE24(int index);

    /**
     * Gets a 32-bit big endian integer at the specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 4)
     */
    int   getBE32(int index);

    /**
     * Gets a 48-bit big endian integer at the specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 6)
     */
    long  getBE48(int index);

    /**
     * Gets a 64-bit big endian integer at the specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 8)
     */
    long  getBE64(int index);

    /**
     * Gets a 16-bit little endian integer at the specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 2)
     */
    short getLE16(int index);

    /**
     * Gets a 24-bit little endian integer at the specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 3)
     */
    int   getLE24(int index);

    /**
     * Gets a 32-bit little endian integer at the specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 4)
     */
    int   getLE32(int index);

    /**
     * Gets a 48-bit little endian integer at the specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 6)
     */
    long  getLE48(int index);

    /**
     * Gets a 64-bit little endian integer at the specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 8)
     */
    long  getLE64(int index);

    /**
     * Copies the partial or entire content of this array to the specified
     * destination.  This operation is identical with the following pseudo
     * code.
     * <pre>
     * {@link System#arraycopy(Object, int, Object, int, int) System.arraycopy}(array, index, dst, dst.firstIndex(), dst.length());
     * </pre>
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} -
     *         destination's length)
     */
    void get(int index, ByteArray dst);

    /**
     * Copies the partial or entire content of this array to the specified
     * destination.  This operation is identical with the following pseudo
     * code.
     * <pre>
     * {@link System#arraycopy(Object, int, Object, int, int) System.arraycopy}(array, index, dst, dstIndex, length);
     * </pre>
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} -
     *         destination's length)
     */
    void get(int index, ByteArray dst, int dstIndex, int length);

    /**
     * Copies the partial or entire content of this array to the specified
     * destination.  This operation is identical with the following pseudo
     * code.
     * <pre>
     * {@link System#arraycopy(Object, int, Object, int, int) System.arraycopy}(array, index, dst, 0, dst.length);
     * </pre>
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} -
     *         destination's length)
     */
    void get(int index, byte[] dst);

    /**
     * Copies the partial or entire content of this array to the specified
     * destination.  This operation is identical with the following pseudo
     * code.
     * <pre>
     * {@link System#arraycopy(Object, int, Object, int, int) System.arraycopy}(array, index, dst, dstIndex, length);
     * </pre>
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} -
     *         destination's length)
     */
    void get(int index, byte[] dst, int dstIndex, int length);

    /**
     * Copies the partial or entire content of this array to the specified
     * destination.  This operation is identical with the following pseudo
     * code.  Please note the position of the destination buffer changes.
     * <pre>
     * {@link System#arraycopy(Object, int, Object, int, int) System.arraycopy}(array, index, dst, dst.position(), dst.remaining());
     * dst.position(dst.limit());
     * </pre>
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} -
     *         destination's length)
     */
    void get(int index, ByteBuffer dst);

    /**
     * Sets the specified byte value at the specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 1).
     */
    void set8   (int index, byte  value);

    /**
     * Sets the specified value as a 16-bit big endian integer at the
     * specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 2).
     */
    void setBE16(int index, short value);

    /**
     * Sets the specified value as a 24-bit big endian integer at the
     * specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 3).
     */
    void setBE24(int index, int   value);

    /**
     * Sets the specified value as a 32-bit big endian integer at the
     * specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 4).
     */
    void setBE32(int index, int   value);

    /**
     * Sets the specified value as a 48-bit big endian integer at the
     * specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 6).
     */
    void setBE48(int index, long  value);

    /**
     * Sets the specified value as a 64-bit big endian integer at the
     * specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 8).
     */
    void setBE64(int index, long  value);

    /**
     * Sets the specified value as a 16-bit little endian integer at the
     * specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 2).
     */
    void setLE16(int index, short value);

    /**
     * Sets the specified value as a 24-bit little endian integer at the
     * specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 3).
     */
    void setLE24(int index, int   value);

    /**
     * Sets the specified value as a 32-bit little endian integer at the
     * specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 4).
     */
    void setLE32(int index, int   value);

    /**
     * Sets the specified value as a 48-bit little endian integer at the
     * specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 6).
     */
    void setLE48(int index, long  value);

    /**
     * Sets the specified value as a 64-bit little endian integer at the
     * specified index.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - 8).
     */
    void setLE64(int index, long  value);

    /**
     * Copies the specified source into the specified index of this array.
     * This operation is identical with the following pseudo code.
     * <pre>
     * {@link System#arraycopy(Object, int, Object, int, int) System.arraycopy}(src, src.firstIndex(), array, index, src.length());
     * </pre>
     */
    void set(int index, ByteArray src);

    /**
     * Copies the specified source into the specified index of this array.
     * This operation is identical with the following pseudo code.
     * <pre>
     * {@link System#arraycopy(Object, int, Object, int, int) System.arraycopy}(src, srcIndex, array, index, length);
     * </pre>
     */
    void set(int index, ByteArray src, int srcIndex, int length);

    /**
     * Copies the specified source into the specified index of this array.
     * This operation is identical with the following pseudo code.
     * <pre>
     * {@link System#arraycopy(Object, int, Object, int, int) System.arraycopy}(src, 0, array, index, src.length);
     * </pre>
     */
    void set(int index, byte[] src);

    /**
     * Copies the specified source into the specified index of this array.
     * This operation is identical with the following pseudo code.
     * <pre>
     * {@link System#arraycopy(Object, int, Object, int, int) System.arraycopy}(src, srcIndex, array, index, length);
     * </pre>
     */
    void set(int index, byte[] src, int srcIndex, int length);

    /**
     * Copies the specified source into the specified index of this array.
     * This operation is identical with the following pseudo code.
     * Please note the position of the destination buffer changes.
     * <pre>
     * {@link System#arraycopy(Object, int, Object, int, int) System.arraycopy}(src, src.position(), array, index, src.remaining());
     * src.position(src.limit());
     * </pre>
     */
    void set(int index, ByteBuffer src);

    /**
     * Returns the index of the first occurrence of the specified byte value,
     * starting the search from the specified index and finishing the search
     * at {@linkplain #endIndex() the end index}.
     *
     * @return if found, the index of the first occurrence.
     *         Otherwise {@link #NOT_FOUND}.
     */
    long indexOf(int fromIndex, byte value);

    /**
     * Returns the index of this array where the specified index finder
     * gave the first positive response, starting the search from the
     * specified index and finishing the search at
     * {@linkplain #endIndex() the end index}.
     *
     * @return if found, the index of the first occurrence.
     *         Otherwise {@link #NOT_FOUND}.
     */
    long indexOf(int fromIndex, ByteArrayIndexFinder indexFinder);

    /**
     * Returns the index of the first occurrence of the specified byte value,
     * starting the search from {@linkplain #endIndex() the end index} and
     * finishing the search at the specified index (reversed order).
     *
     * @return if found, the index of the first occurrence.
     *         Otherwise {@link #NOT_FOUND}.
     */
    long lastIndexOf(int fromIndex, byte value);

    /**
     * Returns the index of this array where the specified index finder
     * gave the first positive response, starting the search from
     * {@linkplain #endIndex() the end index} and finishing the search at the
     * specified index (reversed order).
     *
     * @return if found, the index of the first occurrence.
     *         Otherwise {@link #NOT_FOUND}.
     */
    long lastIndexOf(int fromIndex, ByteArrayIndexFinder indexFinder);

    /**
     * Writes the entire content of this array to the specified output stream.
     * If this array is {@linkplain #empty() empty}, nothing will happen.
     * Calling this method is identical with the following code:
     * <pre>
     * array.copyTo(out, array.firstIndex(), array.length());
     * </pre>
     * Also, this operation is identical with the following pseudo code:
     * <pre>
     * out.write(array);
     * </pre>
     *
     * @throws IOException if an exception is raised by the specified stream
     */
    void copyTo(OutputStream out) throws IOException;

    /**
     * Writes the entire or partial content of this array to the specified
     * output stream.  If the specified length is {@code 0}, nothing will
     * happen. This operation is identical with the following pseudo code:
     * <pre>
     * out.write(array, index, length);
     * </pre>
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - the specified length)
     * @throws IOException if an exception is raised by the specified stream
     */
    void copyTo(OutputStream out, int index, int length) throws IOException;

    /**
     * Writes the entire content of this array to the specified channel.
     * If this array is {@linkplain #empty() empty}, nothing will happen.
     * Calling this method is identical with the following code:
     * <pre>
     * array.copyTo(channel, array.firstIndex(), array.length());
     * </pre>
     * Also, this operation is identical with the following pseudo code:
     * <pre>
     * int oldPosition = array.position();
     * try {
     *     channel.write(array);
     * } finally {
     *     array.position(oldPosition);
     * }
     * </pre>
     *
     * @return the number of actually written bytes, which may be {@code 0}
     * @throws IOException if an exception is raised by the specified stream
     */
    int  copyTo(WritableByteChannel out) throws IOException;

    /**
     * Writes the entire or partial content of this array to the specified
     * channel.  If the specified length is {@code 0}, nothing will happen.
     * This operation is identical with the following pseudo code:
     * <pre>
     * int oldPosition = array.position();
     * int oldLimit = array.limit();
     *
     * array.position(index);
     * array.limit(index + length);
     * try {
     *     channel.write(array);
     * } finally {
     *     array.position(oldPosition);
     *     array.limit(oldLimit);
     * }
     * </pre>
     *
     * @return the number of actually written bytes, which may be {@code 0}
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - the specified length)
     * @throws IOException if an exception is raised by the specified stream
     */
    int  copyTo(WritableByteChannel out, int index, int length) throws IOException;

    /**
     * Writes the entire content of this array to the specified channel.
     * Gathering write operation will be utilized whenever possible.
     * If this array is {@linkplain #empty() empty}, nothing will happen.
     * Calling this method is identical with the following code:
     * <pre>
     * array.copyTo(channel, array.firstIndex(), array.length());
     * </pre>
     * Also, this operation is identical with the following pseudo code:
     * <pre>
     * int oldPosition = array.position();
     * try {
     *     channel.write(array);
     * } finally {
     *     array.position(oldPosition);
     * }
     * </pre>
     *
     * @return the number of actually written bytes, which may be {@code 0}
     * @throws IOException if an exception is raised by the specified stream
     */
    int  copyTo(GatheringByteChannel out) throws IOException;

    /**
     * Writes the entire or partial content of this array to the specified
     * channel.  Gathering write operation will be utilized whenever possible.
     * If the specified length is {@code 0}, nothing will happen.
     * This operation is identical with the following pseudo code:
     * <pre>
     * int oldPosition = array.position();
     * int oldLimit = array.limit();
     *
     * array.position(index);
     * array.limit(index + length);
     * try {
     *     channel.write(array);
     * } finally {
     *     array.position(oldPosition);
     *     array.limit(oldLimit);
     * }
     * </pre>
     *
     * @return the number of actually written bytes, which may be {@code 0}
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - the specified length)
     * @throws IOException if an exception is raised by the specified stream
     */
    int  copyTo(GatheringByteChannel out, int index, int length) throws IOException;

    /**
     * Returns a NIO buffer which is either a wrapper or a deep copy of this
     * array.  There's no guarantee that this method will always return a
     * wrapper or will always return a deep copy.  Calling this method is
     * identical with the following code:
     * <pre>
     * array.getByteBuffer(array.firstIndex(), array.length());
     * </pre>
     */
    ByteBuffer getByteBuffer();

    /**
     * Returns a NIO buffer which is either a wrapper or a deep copy of this
     * array.  There's no guarantee that this method will always return a
     * wrapper or will always return a deep copy.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified index is less than
     *         {@linkplain #firstIndex() the first index} or
     *         greater than ({@linkplain #endIndex() the end index} - the specified length)
     */
    ByteBuffer getByteBuffer(int index, int length);

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
     * Please note that there's no need for the two arrays to have the same
     * {@linkplain #firstIndex() first index} to meet these conditions.  This
     * method also returns {@code false} for {@code null} and an object which
     * is not an instance of {@link ByteArray} type.
     */
    boolean equals(Object obj);

    /**
     * Compares the content of the specified array to the content of this
     * array.  Comparison is performed in the same manner with the string
     * comparison functions in various languages such as {@code strcmp},
     * {@code memcmp} and {@link String#compareTo(String)}.
     */
    int compareTo(ByteArray array);

    /**
     * Returns the string representation of this array.  This method doesn't
     * necessarily return the whole content of the array, but could return
     * a string which contains just a few meta data such as
     * {@linkplain #firstIndex() the first index} and
     * {@linkplain #endIndex() the end index}.
     */
    String toString();
}
