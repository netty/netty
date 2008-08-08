/**
 * Abstraction for a sequence of bytes (octets), so called generic byte array.
 * <p>
 * Primitive byte array ({@code byte[]}) and
 * {@linkplain java.nio.ByteBuffer NIO buffer} are most frequently used data
 * structure for Java I/O. {@link net.gleamynode.netty.array.ByteArray}
 * provides an abstract view for one or more primitive byte arrays and NIO
 * buffers.
 * <p>
 * The advantage of this abstraction is that it hides the internal
 * implementation of a byte array.  For example, it could be implemented as a
 * {@linkplain net.gleamynode.netty.array.CompositeByteArray composite} of
 * more than one primitive byte array and NIO buffer to reduce the number of
 * memory copy operations to increase the performance of an I/O-intensive
 * application.
 * <p>
 * {@link net.gleamynode.netty.array.ByteArrayBuffer} is also provided as an
 * extension of {@link net.gleamynode.netty.array.ByteArray}.  It has
 * various sequential read and write operations like an NIO buffer's getter
 * and putter methods for more convenient construction of a byte array.
 *
 * @apiviz.landmark
 * @apiviz.exclude ^java
 */
package net.gleamynode.netty.array;