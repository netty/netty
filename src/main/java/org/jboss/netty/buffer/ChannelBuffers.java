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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.util.CharsetUtil;


/**
 * Creates a new {@link ChannelBuffer} by allocating new space or by wrapping
 * or copying existing byte arrays, byte buffers and a string.
 *
 * <h3>Use static import</h3>
 * This classes is intended to be used with Java 5 static import statement:
 *
 * <pre>
 * import static org.jboss.netty.buffer.{@link ChannelBuffers}.*;
 *
 * {@link ChannelBuffer} heapBuffer    = buffer(128);
 * {@link ChannelBuffer} directBuffer  = directBuffer(256);
 * {@link ChannelBuffer} dynamicBuffer = dynamicBuffer(512);
 * {@link ChannelBuffer} wrappedBuffer = wrappedBuffer(new byte[128], new byte[256]);
 * {@link ChannelBuffer} copiedBuffe r = copiedBuffer({@link ByteBuffer}.allocate(128));
 * </pre>
 *
 * <h3>Allocating a new buffer</h3>
 *
 * Three buffer types are provided out of the box.
 *
 * <ul>
 * <li>{@link #buffer(int)} allocates a new fixed-capacity heap buffer.</li>
 * <li>{@link #directBuffer(int)} allocates a new fixed-capacity direct buffer.</li>
 * <li>{@link #dynamicBuffer(int)} allocates a new dynamic-capacity heap
 *     buffer, whose capacity increases automatically as needed by a write
 *     operation.</li>
 * </ul>
 *
 * <h3>Creating a wrapped buffer</h3>
 *
 * Wrapped buffer is a buffer which is a view of one or more existing
 * byte arrays and byte buffers.  Any changes in the content of the original
 * array or buffer will be visible in the wrapped buffer.  Various wrapper
 * methods are provided and their name is all {@code wrappedBuffer()}.
 * You might want to take a look at the methods that accept varargs closely if
 * you want to create a buffer which is composed of more than one array to
 * reduce the number of memory copy.
 *
 * <h3>Creating a copied buffer</h3>
 *
 * Copied buffer is a deep copy of one or more existing byte arrays, byte
 * buffers or a string.  Unlike a wrapped buffer, there's no shared data
 * between the original data and the copied buffer.  Various copy methods are
 * provided and their name is all {@code copiedBuffer()}.  It is also convenient
 * to use this operation to merge multiple buffers into one buffer.
 *
 * <h3>Miscellaneous utility methods</h3>
 *
 * This class also provides various utility methods to help implementation
 * of a new buffer type, generation of hex dump and swapping an integer's
 * byte order.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2269 $, $Date: 2010-05-06 16:37:27 +0900 (Thu, 06 May 2010) $
 *
 * @apiviz.landmark
 * @apiviz.has org.jboss.netty.buffer.ChannelBuffer oneway - - creates
 */
public class ChannelBuffers {

    /**
     * Big endian byte order.
     */
    public static final ByteOrder BIG_ENDIAN = ByteOrder.BIG_ENDIAN;

    /**
     * Little endian byte order.
     */
    public static final ByteOrder LITTLE_ENDIAN = ByteOrder.LITTLE_ENDIAN;

    /**
     * A buffer whose capacity is {@code 0}.
     */
    public static final ChannelBuffer EMPTY_BUFFER = new BigEndianHeapChannelBuffer(0);

    private static final char[] HEXDUMP_TABLE = new char[256 * 4];

    static {
        final char[] DIGITS = "0123456789abcdef".toCharArray();
        for (int i = 0; i < 256; i ++) {
            HEXDUMP_TABLE[(i << 1) + 0] = DIGITS[i >>> 4 & 0x0F];
            HEXDUMP_TABLE[(i << 1) + 1] = DIGITS[i >>> 0 & 0x0F];
        }
    }

    /**
     * Creates a new big-endian Java heap buffer with the specified
     * {@code capacity}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer buffer(int capacity) {
        return buffer(BIG_ENDIAN, capacity);
    }

    /**
     * Creates a new Java heap buffer with the specified {@code endianness}
     * and {@code capacity}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer buffer(ByteOrder endianness, int capacity) {
        if (endianness == BIG_ENDIAN) {
            if (capacity == 0) {
                return EMPTY_BUFFER;
            }
            return new BigEndianHeapChannelBuffer(capacity);
        } else if (endianness == LITTLE_ENDIAN) {
            if (capacity == 0) {
                return EMPTY_BUFFER;
            }
            return new LittleEndianHeapChannelBuffer(capacity);
        } else {
            throw new NullPointerException("endianness");
        }
    }

    /**
     * Creates a new big-endian direct buffer with the specified
     * {@code capacity}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer directBuffer(int capacity) {
        return directBuffer(BIG_ENDIAN, capacity);
    }

    /**
     * Creates a new direct buffer with the specified {@code endianness} and
     * {@code capacity}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer directBuffer(ByteOrder endianness, int capacity) {
        if (endianness == null) {
            throw new NullPointerException("endianness");
        }
        if (capacity == 0) {
            return EMPTY_BUFFER;
        }

        ChannelBuffer buffer = new ByteBufferBackedChannelBuffer(
                ByteBuffer.allocateDirect(capacity).order(endianness));
        buffer.clear();
        return buffer;
    }

    /**
     * Creates a new big-endian dynamic buffer whose estimated data length is
     * {@code 256} bytes.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer dynamicBuffer() {
        return dynamicBuffer(BIG_ENDIAN, 256);
    }

    public static ChannelBuffer dynamicBuffer(ChannelBufferFactory factory) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }

        return new DynamicChannelBuffer(factory.getDefaultOrder(), 256, factory);
    }

    /**
     * Creates a new big-endian dynamic buffer with the specified estimated
     * data length.  More accurate estimation yields less unexpected
     * reallocation overhead.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer dynamicBuffer(int estimatedLength) {
        return dynamicBuffer(BIG_ENDIAN, estimatedLength);
    }

    /**
     * Creates a new dynamic buffer with the specified endianness and
     * the specified estimated data length.  More accurate estimation yields
     * less unexpected reallocation overhead.  The new buffer's
     * {@code readerIndex} and {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer dynamicBuffer(ByteOrder endianness, int estimatedLength) {
        return new DynamicChannelBuffer(endianness, estimatedLength);
    }

    /**
     * Creates a new big-endian dynamic buffer with the specified estimated
     * data length using the specified factory.  More accurate estimation yields
     * less unexpected reallocation overhead.  The new buffer's {@code readerIndex}
     * and {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer dynamicBuffer(int estimatedLength, ChannelBufferFactory factory) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }

        return new DynamicChannelBuffer(factory.getDefaultOrder(), estimatedLength, factory);
    }

    /**
     * Creates a new dynamic buffer with the specified endianness and
     * the specified estimated data length using the specified factory.
     * More accurate estimation yields less unexpected reallocation overhead.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer dynamicBuffer(ByteOrder endianness, int estimatedLength, ChannelBufferFactory factory) {
        return new DynamicChannelBuffer(endianness, estimatedLength, factory);
    }

    /**
     * Creates a new big-endian buffer which wraps the specified {@code array}.
     * A modification on the specified array's content will be visible to the
     * returned buffer.
     */
    public static ChannelBuffer wrappedBuffer(byte[] array) {
        return wrappedBuffer(BIG_ENDIAN, array);
    }

    /**
     * Creates a new buffer which wraps the specified {@code array} with the
     * specified {@code endianness}.  A modification on the specified array's
     * content will be visible to the returned buffer.
     */
    public static ChannelBuffer wrappedBuffer(ByteOrder endianness, byte[] array) {
        if (endianness == BIG_ENDIAN) {
            if (array.length == 0) {
                return EMPTY_BUFFER;
            }
            return new BigEndianHeapChannelBuffer(array);
        } else if (endianness == LITTLE_ENDIAN) {
            if (array.length == 0) {
                return EMPTY_BUFFER;
            }
            return new LittleEndianHeapChannelBuffer(array);
        } else {
            throw new NullPointerException("endianness");
        }
    }

    /**
     * Creates a new big-endian buffer which wraps the sub-region of the
     * specified {@code array}.  A modification on the specified array's
     * content will be visible to the returned buffer.
     */
    public static ChannelBuffer wrappedBuffer(byte[] array, int offset, int length) {
        return wrappedBuffer(BIG_ENDIAN, array, offset, length);
    }

    /**
     * Creates a new buffer which wraps the sub-region of the specified
     * {@code array} with the specified {@code endianness}.  A modification on
     * the specified array's content will be visible to the returned buffer.
     */
    public static ChannelBuffer wrappedBuffer(ByteOrder endianness, byte[] array, int offset, int length) {
        if (endianness == null) {
            throw new NullPointerException("endianness");
        }
        if (offset == 0) {
            if (length == array.length) {
                return wrappedBuffer(endianness, array);
            } else {
                if (length == 0) {
                    return EMPTY_BUFFER;
                } else {
                    return new TruncatedChannelBuffer(wrappedBuffer(endianness, array), length);
                }
            }
        } else {
            if (length == 0) {
                return EMPTY_BUFFER;
            } else {
                return new SlicedChannelBuffer(wrappedBuffer(endianness, array), offset, length);
            }
        }
    }

    /**
     * Creates a new buffer which wraps the specified NIO buffer's current
     * slice.  A modification on the specified buffer's content will be
     * visible to the returned buffer.
     */
    public static ChannelBuffer wrappedBuffer(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            return EMPTY_BUFFER;
        }
        if (buffer.hasArray()) {
            return wrappedBuffer(buffer.order(), buffer.array(), buffer.arrayOffset() + buffer.position(),buffer.remaining());
        } else {
            return new ByteBufferBackedChannelBuffer(buffer);
        }
    }

    /**
     * Creates a new buffer which wraps the specified buffer's readable bytes.
     * A modification on the specified buffer's content will be visible to the
     * returned buffer.
     */
    public static ChannelBuffer wrappedBuffer(ChannelBuffer buffer) {
        if (buffer.readable()) {
            return buffer.slice();
        } else {
            return EMPTY_BUFFER;
        }
    }

    /**
     * Creates a new big-endian composite buffer which wraps the specified
     * arrays without copying them.  A modification on the specified arrays'
     * content will be visible to the returned buffer.
     */
    public static ChannelBuffer wrappedBuffer(byte[]... arrays) {
        return wrappedBuffer(BIG_ENDIAN, arrays);
    }

    /**
     * Creates a new composite buffer which wraps the specified arrays without
     * copying them.  A modification on the specified arrays' content will be
     * visible to the returned buffer.
     *
     * @param endianness the endianness of the new buffer
     */
    public static ChannelBuffer wrappedBuffer(ByteOrder endianness, byte[]... arrays) {
        switch (arrays.length) {
        case 0:
            break;
        case 1:
            if (arrays[0].length != 0) {
                return wrappedBuffer(endianness, arrays[0]);
            }
            break;
        default:
            // Get the list of the component, while guessing the byte order.
            final List<ChannelBuffer> components = new ArrayList<ChannelBuffer>(arrays.length);
            for (byte[] a: arrays) {
                if (a == null) {
                    break;
                }
                if (a.length > 0) {
                    components.add(wrappedBuffer(endianness, a));
                }
            }
            return compositeBuffer(endianness, components);
        }

        return EMPTY_BUFFER;
    }

    private static ChannelBuffer compositeBuffer(
            ByteOrder endianness, List<ChannelBuffer> components) {
        switch (components.size()) {
        case 0:
            return EMPTY_BUFFER;
        case 1:
            return components.get(0);
        default:
            return new CompositeChannelBuffer(endianness, components);
        }
    }

    /**
     * Creates a new composite buffer which wraps the readable bytes of the
     * specified buffers without copying them.  A modification on the content
     * of the specified buffers will be visible to the returned buffer.
     *
     * @throws IllegalArgumentException
     *         if the specified buffers' endianness are different from each
     *         other
     */
    public static ChannelBuffer wrappedBuffer(ChannelBuffer... buffers) {
        switch (buffers.length) {
        case 0:
            break;
        case 1:
            if (buffers[0].readable()) {
                return wrappedBuffer(buffers[0]);
            }
            break;
        default:
            ByteOrder order = null;
            final List<ChannelBuffer> components = new ArrayList<ChannelBuffer>(buffers.length);
            for (ChannelBuffer c: buffers) {
                if (c == null) {
                    break;
                }
                if (c.readable()) {
                    if (order != null) {
                        if (!order.equals(c.order())) {
                            throw new IllegalArgumentException(
                                    "inconsistent byte order");
                        }
                    } else {
                        order = c.order();
                    }
                    if (c instanceof CompositeChannelBuffer) {
                        // Expand nested composition.
                        components.addAll(
                                ((CompositeChannelBuffer) c).decompose(
                                        c.readerIndex(), c.readableBytes()));
                    } else {
                        // An ordinary buffer (non-composite)
                        components.add(c.slice());
                    }
                }
            }
            return compositeBuffer(order, components);
        }
        return EMPTY_BUFFER;
    }

    /**
     * Creates a new composite buffer which wraps the slices of the specified
     * NIO buffers without copying them.  A modification on the content of the
     * specified buffers will be visible to the returned buffer.
     *
     * @throws IllegalArgumentException
     *         if the specified buffers' endianness are different from each
     *         other
     */
    public static ChannelBuffer wrappedBuffer(ByteBuffer... buffers) {
        switch (buffers.length) {
        case 0:
            break;
        case 1:
            if (buffers[0].hasRemaining()) {
                return wrappedBuffer(buffers[0]);
            }
            break;
        default:
            ByteOrder order = null;
            final List<ChannelBuffer> components = new ArrayList<ChannelBuffer>(buffers.length);
            for (ByteBuffer b: buffers) {
                if (b == null) {
                    break;
                }
                if (b.hasRemaining()) {
                    if (order != null) {
                        if (!order.equals(b.order())) {
                            throw new IllegalArgumentException(
                                    "inconsistent byte order");
                        }
                    } else {
                        order = b.order();
                    }
                    components.add(wrappedBuffer(b));
                }
            }
            return compositeBuffer(order, components);
        }

        return EMPTY_BUFFER;
    }

    /**
     * Creates a new big-endian buffer whose content is a copy of the
     * specified {@code array}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0} and {@code array.length} respectively.
     */
    public static ChannelBuffer copiedBuffer(byte[] array) {
        return copiedBuffer(BIG_ENDIAN, array);
    }

    /**
     * Creates a new buffer with the specified {@code endianness} whose
     * content is a copy of the specified {@code array}.  The new buffer's
     * {@code readerIndex} and {@code writerIndex} are {@code 0} and
     * {@code array.length} respectively.
     */
    public static ChannelBuffer copiedBuffer(ByteOrder endianness, byte[] array) {
        if (endianness == BIG_ENDIAN) {
            if (array.length == 0) {
                return EMPTY_BUFFER;
            }
            return new BigEndianHeapChannelBuffer(array.clone());
        } else if (endianness == LITTLE_ENDIAN) {
            if (array.length == 0) {
                return EMPTY_BUFFER;
            }
            return new LittleEndianHeapChannelBuffer(array.clone());
        } else {
            throw new NullPointerException("endianness");
        }
    }

    /**
     * Creates a new big-endian buffer whose content is a copy of the
     * specified {@code array}'s sub-region.  The new buffer's
     * {@code readerIndex} and {@code writerIndex} are {@code 0} and
     * the specified {@code length} respectively.
     */
    public static ChannelBuffer copiedBuffer(byte[] array, int offset, int length) {
        return copiedBuffer(BIG_ENDIAN, array, offset, length);
    }

    /**
     * Creates a new buffer with the specified {@code endianness} whose
     * content is a copy of the specified {@code array}'s sub-region.  The new
     * buffer's {@code readerIndex} and {@code writerIndex} are {@code 0} and
     * the specified {@code length} respectively.
     */
    public static ChannelBuffer copiedBuffer(ByteOrder endianness, byte[] array, int offset, int length) {
        if (endianness == null) {
            throw new NullPointerException("endianness");
        }
        if (length == 0) {
            return EMPTY_BUFFER;
        }
        byte[] copy = new byte[length];
        System.arraycopy(array, offset, copy, 0, length);
        return wrappedBuffer(endianness, copy);
    }

    /**
     * Creates a new buffer whose content is a copy of the specified
     * {@code buffer}'s current slice.  The new buffer's {@code readerIndex}
     * and {@code writerIndex} are {@code 0} and {@code buffer.remaining}
     * respectively.
     */
    public static ChannelBuffer copiedBuffer(ByteBuffer buffer) {
        int length = buffer.remaining();
        if (length == 0) {
            return EMPTY_BUFFER;
        }
        byte[] copy = new byte[length];
        int position = buffer.position();
        try {
            buffer.get(copy);
        } finally {
            buffer.position(position);
        }
        return wrappedBuffer(buffer.order(), copy);
    }

    /**
     * Creates a new buffer whose content is a copy of the specified
     * {@code buffer}'s readable bytes.  The new buffer's {@code readerIndex}
     * and {@code writerIndex} are {@code 0} and {@code buffer.readableBytes}
     * respectively.
     */
    public static ChannelBuffer copiedBuffer(ChannelBuffer buffer) {
        if (buffer.readable()) {
            return buffer.copy();
        } else {
            return EMPTY_BUFFER;
        }
    }

    /**
     * Creates a new big-endian buffer whose content is a merged copy of
     * the specified {@code arrays}.  The new buffer's {@code readerIndex}
     * and {@code writerIndex} are {@code 0} and the sum of all arrays'
     * {@code length} respectively.
     */
    public static ChannelBuffer copiedBuffer(byte[]... arrays) {
        return copiedBuffer(BIG_ENDIAN, arrays);
    }

    /**
     * Creates a new buffer with the specified {@code endianness} whose
     * content is a merged copy of the specified {@code arrays}.  The new
     * buffer's {@code readerIndex} and {@code writerIndex} are {@code 0}
     * and the sum of all arrays' {@code length} respectively.
     */
    public static ChannelBuffer copiedBuffer(ByteOrder endianness, byte[]... arrays) {
        switch (arrays.length) {
        case 0:
            return EMPTY_BUFFER;
        case 1:
            if (arrays[0].length == 0) {
                return EMPTY_BUFFER;
            } else {
                return copiedBuffer(endianness, arrays[0]);
            }
        }

        // Merge the specified arrays into one array.
        int length = 0;
        for (byte[] a: arrays) {
            if (Integer.MAX_VALUE - length < a.length) {
                throw new IllegalArgumentException(
                        "The total length of the specified arrays is too big.");
            }
            length += a.length;
        }

        if (length == 0) {
            return EMPTY_BUFFER;
        }

        byte[] mergedArray = new byte[length];
        for (int i = 0, j = 0; i < arrays.length; i ++) {
            byte[] a = arrays[i];
            System.arraycopy(a, 0, mergedArray, j, a.length);
            j += a.length;
        }

        return wrappedBuffer(endianness, mergedArray);
    }

    /**
     * Creates a new buffer whose content is a merged copy of the specified
     * {@code buffers}' readable bytes.  The new buffer's {@code readerIndex}
     * and {@code writerIndex} are {@code 0} and the sum of all buffers'
     * {@code readableBytes} respectively.
     *
     * @throws IllegalArgumentException
     *         if the specified buffers' endianness are different from each
     *         other
     */
    public static ChannelBuffer copiedBuffer(ChannelBuffer... buffers) {
        switch (buffers.length) {
        case 0:
            return EMPTY_BUFFER;
        case 1:
            return copiedBuffer(buffers[0]);
        }

        ChannelBuffer[] copiedBuffers = new ChannelBuffer[buffers.length];
        for (int i = 0; i < buffers.length; i ++) {
            copiedBuffers[i] = copiedBuffer(buffers[i]);
        }
        return wrappedBuffer(copiedBuffers);
    }

    /**
     * Creates a new buffer whose content is a merged copy of the specified
     * {@code buffers}' slices.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0} and the sum of all buffers'
     * {@code remaining} respectively.
     *
     * @throws IllegalArgumentException
     *         if the specified buffers' endianness are different from each
     *         other
     */
    public static ChannelBuffer copiedBuffer(ByteBuffer... buffers) {
        switch (buffers.length) {
        case 0:
            return EMPTY_BUFFER;
        case 1:
            return copiedBuffer(buffers[0]);
        }

        ChannelBuffer[] copiedBuffers = new ChannelBuffer[buffers.length];
        for (int i = 0; i < buffers.length; i ++) {
            copiedBuffers[i] = copiedBuffer(buffers[i]);
        }
        return wrappedBuffer(copiedBuffers);
    }

    /**
     * Creates a new big-endian buffer whose content is the specified
     * {@code string} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ChannelBuffer copiedBuffer(CharSequence string, Charset charset) {
        return copiedBuffer(BIG_ENDIAN, string, charset);
    }

    /**
     * Creates a new big-endian buffer whose content is a subregion of
     * the specified {@code string} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ChannelBuffer copiedBuffer(
            CharSequence string, int offset, int length, Charset charset) {
        return copiedBuffer(BIG_ENDIAN, string, offset, length, charset);
    }

    /**
     * Creates a new buffer with the specified {@code endianness} whose
     * content is the specified {@code string} encoded in the specified
     * {@code charset}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0} and the length of the encoded string
     * respectively.
     */
    public static ChannelBuffer copiedBuffer(ByteOrder endianness, CharSequence string, Charset charset) {
        if (string == null) {
            throw new NullPointerException("string");
        }

        if (string instanceof CharBuffer) {
            return copiedBuffer(endianness, (CharBuffer) string, charset);
        }

        return copiedBuffer(endianness, CharBuffer.wrap(string), charset);
    }

    /**
     * Creates a new buffer with the specified {@code endianness} whose
     * content is a subregion of the specified {@code string} encoded in the
     * specified {@code charset}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0} and the length of the encoded string
     * respectively.
     */
    public static ChannelBuffer copiedBuffer(
            ByteOrder endianness, CharSequence string, int offset, int length, Charset charset) {
        if (string == null) {
            throw new NullPointerException("string");
        }
        if (length == 0) {
            return EMPTY_BUFFER;
        }

        if (string instanceof CharBuffer) {
            CharBuffer buf = (CharBuffer) string;
            if (buf.hasArray()) {
                return copiedBuffer(
                        endianness,
                        buf.array(),
                        buf.arrayOffset() + buf.position() + offset,
                        length, charset);
            }

            buf = buf.slice();
            buf.limit(length);
            buf.position(offset);
            return copiedBuffer(endianness, buf, charset);
        }

        return copiedBuffer(
                endianness, CharBuffer.wrap(string, offset, offset + length),
                charset);
    }

    /**
     * Creates a new big-endian buffer whose content is the specified
     * {@code array} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ChannelBuffer copiedBuffer(char[] array, Charset charset) {
        return copiedBuffer(BIG_ENDIAN, array, 0, array.length, charset);
    }

    /**
     * Creates a new big-endian buffer whose content is a subregion of
     * the specified {@code array} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ChannelBuffer copiedBuffer(
            char[] array, int offset, int length, Charset charset) {
        return copiedBuffer(BIG_ENDIAN, array, offset, length, charset);
    }

    /**
     * Creates a new buffer with the specified {@code endianness} whose
     * content is the specified {@code array} encoded in the specified
     * {@code charset}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0} and the length of the encoded string
     * respectively.
     */
    public static ChannelBuffer copiedBuffer(ByteOrder endianness, char[] array, Charset charset) {
        return copiedBuffer(endianness, array, 0, array.length, charset);
    }

    /**
     * Creates a new buffer with the specified {@code endianness} whose
     * content is a subregion of the specified {@code array} encoded in the
     * specified {@code charset}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0} and the length of the encoded string
     * respectively.
     */
    public static ChannelBuffer copiedBuffer(
            ByteOrder endianness, char[] array, int offset, int length, Charset charset) {
        if (array == null) {
            throw new NullPointerException("array");
        }
        if (length == 0) {
            return EMPTY_BUFFER;
        }
        return copiedBuffer(
                endianness, CharBuffer.wrap(array, offset, length), charset);
    }

    private static ChannelBuffer copiedBuffer(ByteOrder endianness, CharBuffer buffer, Charset charset) {
        CharBuffer src = buffer;
        ByteBuffer dst = ChannelBuffers.encodeString(src, charset);
        ChannelBuffer result = wrappedBuffer(endianness, dst.array());
        result.writerIndex(dst.remaining());
        return result;
    }

    /**
     * @deprecated Use {@link #copiedBuffer(CharSequence, Charset)} instead.
     */
    @Deprecated
    public static ChannelBuffer copiedBuffer(String string, String charsetName) {
        return copiedBuffer(string, Charset.forName(charsetName));
    }

    /**
     * @deprecated Use {@link #copiedBuffer(ByteOrder, CharSequence, Charset)} instead.
     */
    @Deprecated
    public static ChannelBuffer copiedBuffer(ByteOrder endianness, String string, String charsetName) {
        return copiedBuffer(endianness, string, Charset.forName(charsetName));
    }

    /**
     * Creates a read-only buffer which disallows any modification operations
     * on the specified {@code buffer}.  The new buffer has the same
     * {@code readerIndex} and {@code writerIndex} with the specified
     * {@code buffer}.
     */
    public static ChannelBuffer unmodifiableBuffer(ChannelBuffer buffer) {
        if (buffer instanceof ReadOnlyChannelBuffer) {
            buffer = ((ReadOnlyChannelBuffer) buffer).unwrap();
        }
        return new ReadOnlyChannelBuffer(buffer);
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified buffer's readable bytes.
     */
    public static String hexDump(ChannelBuffer buffer) {
        return hexDump(buffer, buffer.readerIndex(), buffer.readableBytes());
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified buffer's sub-region.
     */
    public static String hexDump(ChannelBuffer buffer, int fromIndex, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length: " + length);
        }
        if (length == 0) {
            return "";
        }

        int endIndex = fromIndex + length;
        char[] buf = new char[length << 1];

        int srcIdx = fromIndex;
        int dstIdx = 0;
        for (; srcIdx < endIndex; srcIdx ++, dstIdx += 2) {
            System.arraycopy(
                    HEXDUMP_TABLE, buffer.getUnsignedByte(srcIdx) << 1,
                    buf, dstIdx, 2);
        }

        return new String(buf);
    }

    /**
     * Calculates the hash code of the specified buffer.  This method is
     * useful when implementing a new buffer type.
     */
    public static int hashCode(ChannelBuffer buffer) {
        final int aLen = buffer.readableBytes();
        final int intCount = aLen >>> 2;
        final int byteCount = aLen & 3;

        int hashCode = 1;
        int arrayIndex = buffer.readerIndex();
        if (buffer.order() == BIG_ENDIAN) {
            for (int i = intCount; i > 0; i --) {
                hashCode = 31 * hashCode + buffer.getInt(arrayIndex);
                arrayIndex += 4;
            }
        } else {
            for (int i = intCount; i > 0; i --) {
                hashCode = 31 * hashCode + swapInt(buffer.getInt(arrayIndex));
                arrayIndex += 4;
            }
        }

        for (int i = byteCount; i > 0; i --) {
            hashCode = 31 * hashCode + buffer.getByte(arrayIndex ++);
        }

        if (hashCode == 0) {
            hashCode = 1;
        }

        return hashCode;
    }

    /**
     * Returns {@code true} if and only if the two specified buffers are
     * identical to each other as described in {@code ChannelBuffer#equals(Object)}.
     * This method is useful when implementing a new buffer type.
     */
    public static boolean equals(ChannelBuffer bufferA, ChannelBuffer bufferB) {
        final int aLen = bufferA.readableBytes();
        if (aLen != bufferB.readableBytes()) {
            return false;
        }

        final int longCount = aLen >>> 3;
        final int byteCount = aLen & 7;

        int aIndex = bufferA.readerIndex();
        int bIndex = bufferB.readerIndex();

        if (bufferA.order() == bufferB.order()) {
            for (int i = longCount; i > 0; i --) {
                if (bufferA.getLong(aIndex) != bufferB.getLong(bIndex)) {
                    return false;
                }
                aIndex += 8;
                bIndex += 8;
            }
        } else {
            for (int i = longCount; i > 0; i --) {
                if (bufferA.getLong(aIndex) != swapLong(bufferB.getLong(bIndex))) {
                    return false;
                }
                aIndex += 8;
                bIndex += 8;
            }
        }

        for (int i = byteCount; i > 0; i --) {
            if (bufferA.getByte(aIndex) != bufferB.getByte(bIndex)) {
                return false;
            }
            aIndex ++;
            bIndex ++;
        }

        return true;
    }

    /**
     * Compares the two specified buffers as described in {@link ChannelBuffer#compareTo(ChannelBuffer)}.
     * This method is useful when implementing a new buffer type.
     */
    public static int compare(ChannelBuffer bufferA, ChannelBuffer bufferB) {
        final int aLen = bufferA.readableBytes();
        final int bLen = bufferB.readableBytes();
        final int minLength = Math.min(aLen, bLen);
        final int uintCount = minLength >>> 2;
        final int byteCount = minLength & 3;

        int aIndex = bufferA.readerIndex();
        int bIndex = bufferB.readerIndex();

        if (bufferA.order() == bufferB.order()) {
            for (int i = uintCount; i > 0; i --) {
                long va = bufferA.getUnsignedInt(aIndex);
                long vb = bufferB.getUnsignedInt(bIndex);
                if (va > vb) {
                    return 1;
                } else if (va < vb) {
                    return -1;
                }
                aIndex += 4;
                bIndex += 4;
            }
        } else {
            for (int i = uintCount; i > 0; i --) {
                long va = bufferA.getUnsignedInt(aIndex);
                long vb = swapInt(bufferB.getInt(bIndex)) & 0xFFFFFFFFL;
                if (va > vb) {
                    return 1;
                } else if (va < vb) {
                    return -1;
                }
                aIndex += 4;
                bIndex += 4;
            }
        }

        for (int i = byteCount; i > 0; i --) {
            byte va = bufferA.getByte(aIndex);
            byte vb = bufferB.getByte(bIndex);
            if (va > vb) {
                return 1;
            } else if (va < vb) {
                return -1;
            }
            aIndex ++;
            bIndex ++;
        }

        return aLen - bLen;
    }

    /**
     * The default implementation of {@link ChannelBuffer#indexOf(int, int, byte)}.
     * This method is useful when implementing a new buffer type.
     */
    public static int indexOf(ChannelBuffer buffer, int fromIndex, int toIndex, byte value) {
        if (fromIndex <= toIndex) {
            return firstIndexOf(buffer, fromIndex, toIndex, value);
        } else {
            return lastIndexOf(buffer, fromIndex, toIndex, value);
        }
    }

    /**
     * The default implementation of {@link ChannelBuffer#indexOf(int, int, ChannelBufferIndexFinder)}.
     * This method is useful when implementing a new buffer type.
     */
    public static int indexOf(ChannelBuffer buffer, int fromIndex, int toIndex, ChannelBufferIndexFinder indexFinder) {
        if (fromIndex <= toIndex) {
            return firstIndexOf(buffer, fromIndex, toIndex, indexFinder);
        } else {
            return lastIndexOf(buffer, fromIndex, toIndex, indexFinder);
        }
    }

    /**
     * Toggles the endianness of the specified 16-bit short integer.
     */
    public static short swapShort(short value) {
        return (short) (value << 8 | value >>> 8 & 0xff);
    }

    /**
     * Toggles the endianness of the specified 24-bit medium integer.
     */
    public static int swapMedium(int value) {
        return value << 16 & 0xff0000 | value & 0xff00 | value >>> 16 & 0xff;
    }

    /**
     * Toggles the endianness of the specified 32-bit integer.
     */
    public static int swapInt(int value) {
        return swapShort((short) value) <<  16 |
               swapShort((short) (value >>> 16)) & 0xffff;
    }

    /**
     * Toggles the endianness of the specified 64-bit long integer.
     */
    public static long swapLong(long value) {
        return (long) swapInt((int) value) <<  32 |
                      swapInt((int) (value >>> 32)) & 0xffffffffL;
    }

    private static int firstIndexOf(ChannelBuffer buffer, int fromIndex, int toIndex, byte value) {
        fromIndex = Math.max(fromIndex, 0);
        if (fromIndex >= toIndex || buffer.capacity() == 0) {
            return -1;
        }

        for (int i = fromIndex; i < toIndex; i ++) {
            if (buffer.getByte(i) == value) {
                return i;
            }
        }

        return -1;
    }

    private static int lastIndexOf(ChannelBuffer buffer, int fromIndex, int toIndex, byte value) {
        fromIndex = Math.min(fromIndex, buffer.capacity());
        if (fromIndex < 0 || buffer.capacity() == 0) {
            return -1;
        }

        for (int i = fromIndex - 1; i >= toIndex; i --) {
            if (buffer.getByte(i) == value) {
                return i;
            }
        }

        return -1;
    }

    private static int firstIndexOf(ChannelBuffer buffer, int fromIndex, int toIndex, ChannelBufferIndexFinder indexFinder) {
        fromIndex = Math.max(fromIndex, 0);
        if (fromIndex >= toIndex || buffer.capacity() == 0) {
            return -1;
        }

        for (int i = fromIndex; i < toIndex; i ++) {
            if (indexFinder.find(buffer, i)) {
                return i;
            }
        }

        return -1;
    }

    private static int lastIndexOf(ChannelBuffer buffer, int fromIndex, int toIndex, ChannelBufferIndexFinder indexFinder) {
        fromIndex = Math.min(fromIndex, buffer.capacity());
        if (fromIndex < 0 || buffer.capacity() == 0) {
            return -1;
        }

        for (int i = fromIndex - 1; i >= toIndex; i --) {
            if (indexFinder.find(buffer, i)) {
                return i;
            }
        }

        return -1;
    }

    static ByteBuffer encodeString(CharBuffer src, Charset charset) {
        final CharsetEncoder encoder = CharsetUtil.getEncoder(charset);
        final ByteBuffer dst = ByteBuffer.allocate(
                (int) ((double) src.remaining() * encoder.maxBytesPerChar()));
        try {
            CoderResult cr = encoder.encode(src, dst, true);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            cr = encoder.flush(dst);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
        } catch (CharacterCodingException x) {
            throw new IllegalStateException(x);
        }
        dst.flip();
        return dst;
    }

    static String decodeString(ByteBuffer src, Charset charset) {
        final CharsetDecoder decoder = CharsetUtil.getDecoder(charset);
        final CharBuffer dst = CharBuffer.allocate(
                (int) ((double) src.remaining() * decoder.maxCharsPerByte()));
        try {
            CoderResult cr = decoder.decode(src, dst, true);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            cr = decoder.flush(dst);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
        } catch (CharacterCodingException x) {
            throw new IllegalStateException(x);
        }
        return dst.flip().toString();
    }

    private ChannelBuffers() {
        // Unused
    }
}
