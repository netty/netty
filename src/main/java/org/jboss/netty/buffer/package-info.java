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

/**
 * Abstraction of a byte buffer - the fundamental data structure
 * to represent a low-level binary and text message.
 *
 * Netty uses its own buffer API instead of NIO {@link java.nio.ByteBuffer} to
 * represent a sequence of bytes. This approach has significant advantage over
 * using {@link java.nio.ByteBuffer}.  Netty's new buffer type,
 * {@link org.jboss.netty.buffer.ChannelBuffer}, has been designed from ground
 * up to address the problems of {@link java.nio.ByteBuffer} and to meet the
 * daily needs of network application developers.  To list a few cool features:
 * <ul>
 *   <li>You can define your buffer type if necessary.</li>
 *   <li>Transparent zero copy is achieved by built-in composite buffer type.</li>
 *   <li>A dynamic buffer type is provided out-of-the-box, whose capacity is
 *       expanded on demand, just like {@link java.lang.StringBuffer}.</li>
 *   <li>There's no need to call the {@code flip()} method anymore.</li>
 *   <li>It is often faster than {@link java.nio.ByteBuffer}.</li>
 * </ul>
 *
 * <h3>Extensibility</h3>
 *
 * {@link org.jboss.netty.buffer.ChannelBuffer} has rich set of operations
 * optimized for rapid protocol implementation.  For example,
 * {@link org.jboss.netty.buffer.ChannelBuffer} provides various operations
 * for accessing unsigned values and strings and searching for certain byte
 * sequence in a buffer.  You can also extend or wrap existing buffer type
 * to add convenient accessors.  The custom buffer type still implements
 * {@link org.jboss.netty.buffer.ChannelBuffer} interface rather than
 * introducing an incompatible type.
 *
 * <h3>Transparent Zero Copy</h3>
 *
 * To lift up the performance of a network application to the extreme, you need
 * to reduce the number of memory copy operation.  You might have a set of
 * buffers that could be sliced and combined to compose a whole message.  Netty
 * provides a composite buffer which allows you to create a new buffer from the
 * arbitrary number of existing buffers with no memory copy.  For example, a
 * message could be composed of two parts; header and body.  In a modularized
 * application, the two parts could be produced by different modules and
 * assembled later when the message is sent out.
 * <pre>
 * +--------+----------+
 * | header |   body   |
 * +--------+----------+
 * </pre>
 * If {@link java.nio.ByteBuffer} were used, you would have to create a new big
 * buffer and copy the two parts into the new buffer.   Alternatively, you can
 * perform a gathering write operation in NIO, but it restricts you to represent
 * the composite of buffers as an array of {@link java.nio.ByteBuffer}s rather
 * than a single buffer, breaking the abstraction and introducing complicated
 * state management.  Moreover, it's of no use if you are not going to read or
 * write from an NIO channel.
 * <pre>
 * // The composite type is incompatible with the component type.
 * ByteBuffer[] message = new ByteBuffer[] { header, body };
 * </pre>
 * By contrast, {@link org.jboss.netty.buffer.ChannelBuffer} does not have such
 * caveats because it is fully extensible and has a built-in composite buffer
 * type.
 * <pre>
 * // The composite type is compatible with the component type.
 * ChannelBuffer message = ChannelBuffers.wrappedBuffer(header, body);
 *
 * // Therefore, you can even create a composite by mixing a composite and an
 * // ordinary buffer.
 * ChannelBuffer messageWithFooter = ChannelBuffers.wrappedBuffer(message, footer);
 *
 * // Because the composite is still a ChannelBuffer, you can access its content
 * // easily, and the accessor method will behave just like it's a single buffer
 * // even if the region you want to access spans over multiple components.  The
 * // unsigned integer being read here is located across body and footer.
 * messageWithFooter.getUnsignedInt(
 *     messageWithFooter.readableBytes() - footer.readableBytes() - 1);
 * </pre>
 *
 * <h3>Automatic Capacity Extension</h3>
 *
 * Many protocols define variable length messages, which means there's no way to
 * determine the length of a message until you construct the message or it is
 * difficult and inconvenient to calculate the length precisely.  It is just
 * like when you build a {@link java.lang.String}. You often estimate the length
 * of the resulting string and let {@link java.lang.StringBuffer} expand itself
 * on demand.  Netty allows you to do the same via a <em>dynamic</em> buffer
 * which is created by the
 * {@link org.jboss.netty.buffer.ChannelBuffers#dynamicBuffer()} method.
 * <pre>
 * // A new dynamic buffer is created.  Internally, the actual buffer is created
 * // lazily to avoid potentially wasted memory space.
 * ChannelBuffer b = ChannelBuffers.dynamicBuffer(4);
 *
 * // When the first write attempt is made, the internal buffer is created with
 * // the specified initial capacity (4).
 * b.writeByte('1');
 *
 * b.writeByte('2');
 * b.writeByte('3');
 * b.writeByte('4');
 *
 * // When the number of written bytes exceeds the initial capacity (4), the
 * // internal buffer is reallocated automatically with a larger capacity.
 * b.writeByte('5');
 * </pre>
 *
 * <h3>Better Performance</h3>
 *
 * Most frequently used buffer implementation of
 * {@link org.jboss.netty.buffer.ChannelBuffer} is a very thin wrapper of a
 * byte array (i.e. {@code byte[]}).  Unlike {@link java.nio.ByteBuffer}, it has
 * no complicated boundary check and index compensation, and therefore it is
 * easier for a JVM to optimize the buffer access.  More complicated buffer
 * implementation is used only for sliced or composite buffers, and it performs
 * as well as {@link java.nio.ByteBuffer}.
 *
 * @apiviz.landmark
 * @apiviz.exclude ^java\.lang\.
 * @apiviz.exclude ^java\.io\.[^\.]+Stream$
 */
package org.jboss.netty.buffer;
