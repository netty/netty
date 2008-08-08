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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.NoSuchElementException;

/**
 * A {@link ByteArray} with sequential access operations which are similar to
 * what an I/O stream provides.  {@link ByteArrayBuffer} interface has been
 * introduced to make the message construction and decoding as easy as doing
 * the same task with an {@link OutputStream} and {@link InputStream}.
 *
 * <h3>Read operations</h3>
 *
 * The same set of read operations are provided as provided in
 * {@link ByteArray}.  You can read a single byte, various types of integers
 * and a chunk of bytes.   The difference from the array getters is that
 * a read operation discards the read data from itself, consequently increasing
 * {@linkplain #firstIndex() its first index} and decreasing
 * {@linkplain #length() its length}.
 * <p>
 * {@code skip()} methods are also provided for the case that there's no need
 * to retrieve data but to discard a certain number of bytes.
 * <p>
 * As an alternative to {@link #indexOf(int, ByteArrayIndexFinder)},
 * {@link #read(ByteArrayIndexFinder)} method provides an easy way to read
 * a chunk of data until a certain criteria meets.
 *
 * <h3>Write operations</h3>
 *
 * The same set of write operations are provided as provided in
 * {@link ByteArray}.  You can write a single byte, various types of integers
 * and a chunk of bytes.   The difference from the array setters is that
 * a write operation appends the specified data to itself, consequently
 * increasing its {@linkplain #endIndex() end index} and
 * {@linkplain #length() length}.
 *
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public interface ByteArrayBuffer extends ByteArray {

    /**
     * Reads and discards the first single byte.
     * {@linkplain #firstIndex() The first index} increases by {@code 1}.
     * Consequently, {@linkplain #length() the length} of this buffer decreases
     * by {@code 1}.
     *
     * @throws NoSuchElementException
     *         if this buffer is {@linkplain #empty() empty}
     */
    byte  read8   ();

    /**
     * Reads and discards the first 2 bytes as a 16-bit big endian integer.
     * {@linkplain #firstIndex() The first index} increases by {@code 2}.
     * Consequently, {@linkplain #length() the length} of this buffer decreases
     * by {@code 2}.
     *
     * @throws NoSuchElementException
     *         if the {@linkplain #length() length} of this buffer is less than
     *         {@code 2}
     */
    short readBE16();

    /**
     * Reads and discards the first 3 bytes as a 24-bit big endian integer.
     * {@linkplain #firstIndex() The first index} increases by {@code 3}.
     * Consequently, {@linkplain #length() the length} of this buffer decreases
     * by {@code 3}.
     *
     * @throws NoSuchElementException
     *         if the {@linkplain #length() length} of this buffer is less than
     *         {@code 3}
     */
    int   readBE24();

    /**
     * Reads and discards the first 4 bytes as a 32-bit big endian integer.
     * {@linkplain #firstIndex() The first index} increases by {@code 4}.
     * Consequently, {@linkplain #length() the length} of this buffer decreases
     * by {@code 4}.
     *
     * @throws NoSuchElementException
     *         if the {@linkplain #length() length} of this buffer is less than
     *         {@code 4}
     */
    int   readBE32();

    /**
     * Reads and discards the first 6 bytes as a 48-bit big endian integer.
     * {@linkplain #firstIndex() The first index} increases by {@code 6}.
     * Consequently, {@linkplain #length() the length} of this buffer decreases
     * by {@code 6}.
     *
     * @throws NoSuchElementException
     *         if the {@linkplain #length() length} of this buffer is less than
     *         {@code 6}
     */
    long  readBE48();

    /**
     * Reads and discards the first 8 bytes as a 64-bit big endian integer.
     * {@linkplain #firstIndex() The first index} increases by {@code 8}.
     * Consequently, {@linkplain #length() the length} of this buffer decreases
     * by {@code 8}.
     *
     * @throws NoSuchElementException
     *         if the {@linkplain #length() length} of this buffer is less than
     *         {@code 8}
     */
    long  readBE64();

    /**
     * Reads and discards the first 2 bytes as a 16-bit little endian integer.
     * {@linkplain #firstIndex() The first index} increases by {@code 2}.
     * Consequently, {@linkplain #length() the length} of this buffer decreases
     * by {@code 2}.
     *
     * @throws NoSuchElementException
     *         if the {@linkplain #length() length} of this buffer is less than
     *         {@code 2}
     */
    short readLE16();

     /**
     * Reads and discards the first 3 bytes as a 24-bit little endian integer.
     * {@linkplain #firstIndex() The first index} increases by {@code 3}.
     * Consequently, {@linkplain #length() the length} of this buffer decreases
     * by {@code 3}.
     *
     * @throws NoSuchElementException
     *         if the {@linkplain #length() length} of this buffer is less than
     *         {@code 3}
     */
    int   readLE24();

    /**
     * Reads and discards the first 4 bytes as a 32-bit little endian integer.
     * {@linkplain #firstIndex() The first index} increases by {@code 4}.
     * Consequently, {@linkplain #length() the length} of this buffer decreases
     * by {@code 4}.
     *
     * @throws NoSuchElementException
     *         if the {@linkplain #length() length} of this buffer is less than
     *         {@code 4}
     */
    int   readLE32();

    /**
     * Reads and discards the first 6 bytes as a 48-bit little endian integer.
     * {@linkplain #firstIndex() The first index} increases by {@code 6}.
     * Consequently, {@linkplain #length() the length} of this buffer decreases
     * by {@code 6}.
     *
     * @throws NoSuchElementException
     *         if the {@linkplain #length() length} of this buffer is less than
     *         {@code 6}
     */
    long  readLE48();

    /**
     * Reads and discards the first 8 bytes as a 64-bit little endian integer.
     * {@linkplain #firstIndex() The first index} increases by {@code 8}.
     * Consequently, {@linkplain #length() the length} of this buffer decreases
     * by {@code 8}.
     *
     * @throws NoSuchElementException
     *         if the {@linkplain #length() length} of this buffer is less than
     *         {@code 8}
     */
    long  readLE64();

    /**
     * Reads and discards a byte array of an arbitrary length, as much as
     * possible in the extent that neither memory copy nor creation of new
     * wrapper array instance is required.  For instance, if this buffer is
     * composed of more than one array, its first array will be returned and
     * discarded.
     * <p>
     * This method increases {@linkplain #firstIndex() the first index} by the
     * length of the returned array, discarding the read bytes from itself.
     * Consequently, {@linkplain #length() the length} of this buffer decreases
     * by the length of the returned array.
     *
     * @throws NoSuchElementException
     *         if this buffer is {@linkplain #empty() empty}
     */
    ByteArray read();

    /**
     * Reads and discards the specified number of bytes.
     * {@linkplain #firstIndex() The first index} increases by the specified
     * length.  Consequently, {@linkplain #length() the length} of this buffer
     * decreases by the specified length.  If the specified length is
     * {@code 0}, it returns an {@link ByteArray#EMPTY_BUFFER}.
     *
     * @throws NoSuchElementException
     *         if the length of the buffer is less than the specified length
     */
    ByteArray read(int length);

    /**
     * Reads and discards bytes until the specified index finder returns
     * {@code true} starting from {@linkplain #firstIndex() the first index}.
     * Consequently, the first index of this buffer increases by the number of
     * read bytes and {@linkplain #length the length} of this buffer decreases
     * by the number of read bytes.
     *
     * @throws NoSuchElementException
     *         if the index finder returns {@code false} until this method
     *         reaches to the end of the buffer
     */
    ByteArray read(ByteArrayIndexFinder endIndexFinder);

    /**
     * Discards the specified number of bytes starting from
     * {@link #firstIndex() the first index}.  If the specified length is
     * {@code 0}, nothing happens.  Otherwise, the first index increases by
     * the specified length and the length decreases by the specified length.
     *
     * @throws NoSuchElementException
     *         if the length of the buffer is less than the specified length
     */
    void skip(int length);

    /**
     * Discards bytes until the specified index finder returns {@code true}
     * starting from {@linkplain #firstIndex() the first index}.  Consequently,
     * the first index of this buffer increases by the number of discarded
     * bytes and {@linkplain #length the length} of this buffer decreases by
     * the number of discarded bytes.
     *
     * @throws NoSuchElementException
     *         if the index finder returns {@code false} until this method
     *         reaches to the end of the buffer
     */
    void skip(ByteArrayIndexFinder firstIndexFinder);

    /**
     * Appends the specified single byte value to the end of this buffer.
     * Consequently, {@linkplain #endIndex() the end index} and
     * {@linkplain #length() the length} of the buffer will increase
     * by {@code 1}.
     */
    void write8   (byte  value);

    /**
     * Appends the specified value to the end of this buffer as a 16-bit big
     * endian integer.  Consequently, {@linkplain #endIndex() the end index}
     * and {@linkplain #length() the length} of the buffer will increase by
     * {@code 1}.
     */
    void writeBE16(short value);

    /**
     * Appends the specified value to the end of this buffer as a 24-bit big
     * endian integer.  Consequently, {@linkplain #endIndex() the end index}
     * and {@linkplain #length() the length} of the buffer will increase by
     * {@code 3}.
     */
    void writeBE24(int   value);

    /**
     * Appends the specified value to the end of this buffer as a 32-bit big
     * endian integer.  Consequently, {@linkplain #endIndex() the end index}
     * and {@linkplain #length() the length} of the buffer will increase by
     * {@code 4}.
     */
    void writeBE32(int   value);

    /**
     * Appends the specified value to the end of this buffer as a 48-bit big
     * endian integer.  Consequently, {@linkplain #endIndex() the end index}
     * and {@linkplain #length() the length} of the buffer will increase by
     * {@code 6}.
     */
    void writeBE48(long  value);

    /**
     * Appends the specified value to the end of this buffer as a 64-bit big
     * endian integer.  Consequently, {@linkplain #endIndex() the end index}
     * and {@linkplain #length() the length} of the buffer will increase by
     * {@code 8}.
     */
    void writeBE64(long  value);

    /**
     * Appends the specified value to the end of this buffer as a 16-bit little
     * endian integer.  Consequently, {@linkplain #endIndex() the end index}
     * and {@linkplain #length() the length} of the buffer will increase by
     * {@code 1}.
     */
    void writeLE16(short value);

    /**
     * Appends the specified value to the end of this buffer as a 24-bit little
     * endian integer.  Consequently, {@linkplain #endIndex() the end index}
     * and {@linkplain #length() the length} of the buffer will increase by
     * {@code 3}.
     */
    void writeLE24(int   value);

    /**
     * Appends the specified value to the end of this buffer as a 32-bit little
     * endian integer.  Consequently, {@linkplain #endIndex() the end index}
     * and {@linkplain #length() the length} of the buffer will increase by
     * {@code 4}.
     */
    void writeLE32(int   value);

    /**
     * Appends the specified value to the end of this buffer as a 48-bit little
     * endian integer.  Consequently, {@linkplain #endIndex() the end index}
     * and {@linkplain #length() the length} of the buffer will increase by
     * {@code 6}.
     */
    void writeLE48(long  value);

    /**
     * Appends the specified value to the end of this buffer as a 64-bit little
     * endian integer.  Consequently, {@linkplain #endIndex() the end index}
     * and {@linkplain #length() the length} of the buffer will increase by
     * {@code 8}.
     */
    void writeLE64(long  value);

    /**
     * Appends the specified array to the end of this buffer.  The specified
     * array may or may not be copied into this buffer.  The implementation
     * will do its best to avoid memory copy, but there is no guarantee at all.
     * Please note, if no memory copy is performed, modifying the content of
     * the specified array will affect the content of this buffer, too.
     */
    void write(ByteArray src);

    /**
     * Appends the specified array to the end of this buffer.  The specified
     * array may or may not be copied into this buffer.  The implementation
     * will do its best to avoid memory copy, but there is no guarantee at all.
     * Please note, if no memory copy is performed, modifying the content of
     * the specified array will affect the content of this buffer, too.
     *
     * @param srcIndex the index of the first byte to append
     * @param length   the number of bytes to append
     */
    void write(ByteArray src, int srcIndex, int length);

    /**
     * Appends the specified array to the end of this buffer.  The specified
     * array may or may not be copied into this buffer.  The implementation
     * will do its best to avoid memory copy, but there is no guarantee at all.
     * Please note, if no memory copy is performed, modifying the content of
     * the specified array will affect the content of this buffer, too.
     */
    void write(byte[] src);

    /**
     * Appends the specified array to the end of this buffer.  The specified
     * array may or may not be copied into this buffer.  The implementation
     * will do its best to avoid memory copy, but there is no guarantee at all.
     * Please note, if no memory copy is performed, modifying the content of
     * the specified array will affect the content of this buffer, too.
     *
     * @param srcIndex the index of the first byte to append
     * @param length   the number of bytes to append
     */
    void write(byte[] src, int srcIndex, int length);

    /**
     * Appends the specified NIO buffer to the end of this buffer.  The
     * specified buffer may or may not be copied into this buffer.  The
     * implementation will do its best to avoid memory copy, but there is no
     * guarantee at all.  Please note, if no memory copy is performed,
     * modifying the content of the specified NIO buffer will affect the
     * content of this buffer, too.
     */
    void write(ByteBuffer src);
}
