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
package org.jboss.netty.channel;

import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.ssl.SslHandler;


/**
 * A list of {@link ChannelHandler}s which handles or intercepts a
 * {@link ChannelEvent}.
 * <p>
 * {@link ChannelPipeline} implements an advanced form of the
 * <a href="http://java.sun.com/blueprints/corej2eepatterns/Patterns/InterceptingFilter.html">Intercepting
 * Filter</a> pattern to give a user full control over how an event is handled
 * and how {@link ChannelHandler}s in the pipeline interact with each other.
 * <p>
 * Every {@link Channel} has its own pipeline instance.  Each pipeline is
 * created by the {@link ChannelPipelineFactory} specified when a new channel
 * is created by the {@link ChannelFactory}.
 * <p>
 * A user is supposed to have one or more {@link ChannelHandler}s in a
 * pipeline to receive I/O events (e.g. read) and to request I/O operations
 * (e.g. write and close).  For example, a typical server will have the following
 * handlers in each channel's pipeline, but your mileage may vary depending on
 * the complexity and characteristics of the protocol and business logic:
 *
 * <ol>
 * <li>Protocol Decoder - translates binary data (e.g. {@link ChannelBuffer})
 *                        into a Java object.</li>
 * <li>Protocol Encoder - translates a Java object into binary data.</li>
 * <li>{@link ExecutionHandler} - applies a thread model.</li>
 * <li>Business Logic Handler - performs the actual business logic
 *                              (e.g. database access).</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * <p>
 * A {@link ChannelHandler} can be added or removed at any time because a
 * {@link ChannelPipeline} is thread safe.  For example, you can insert a
 * {@link SslHandler} when a sensitive information is about to be exchanged,
 * and remove it after the exchange.
 *
 * <h3>How an event flows in a pipeline</h3>
 * <p>
 * The following diagram describes how events flow upstream and downstream in
 * a {@link ChannelPipeline} typically:
 *
 * <pre>
 *                                      I/O Request
 *                                      via Channel
 *                                           |
 * +-----------------------------------------+----------------+
 * |                     ChannelPipeline     |                |
 * |                                        \|/               |
 * |   +----------------------+  +-----------+------------+   |
 * |   | Upstream Handler  N  |  | Downstream Handler  1  |   |
 * |   +----------+-----------+  +-----------+------------+   |
 * |             /|\                         |                |
 * |              |                         \|/               |
 * |   +----------+-----------+  +-----------+------------+   |
 * |   | Upstream Handler N-1 |  | Downstream Handler  2  |   |
 * |   +----------+-----------+  +-----------+------------+   |
 * |             /|\                         |                |
 * |              |                         \|/               |
 * |   +----------+-----------+  +-----------+------------+   |
 * |   | Upstream Handler N-2 |  | Downstream Handler  3  |   |
 * |   +----------+-----------+  +-----------+------------+   |
 * |              .                          .                |
 * |              .                          .                |
 * |             /|\                         |                |
 * |              |                         \|/               |
 * |   +----------+-----------+  +-----------+------------+   |
 * |   | Upstream Handler  3  |  | Downstream Handler M-2 |   |
 * |   +----------+-----------+  +-----------+------------+   |
 * |             /|\                         |                |
 * |              |                         \|/               |
 * |   +----------+-----------+  +-----------+------------+   |
 * |   | Upstream Handler  2  |  | Downstream Handler M-1 |   |
 * |   +----------+-----------+  +-----------+------------+   |
 * |             /|\                         |                |
 * |              |                         \|/               |
 * |   +----------+-----------+  +-----------+------------+   |
 * |   | Upstream Handler  1  |  | Downstream Handler  M  |   |
 * |   +----------+-----------+  +-----------+------------+   |
 * |             /|\                         |                |
 * +--------------+--------------------------+----------------+
 *                |                         \|/
 * +--------------+--------------------------+----------------+
 * |         I/O Threads (Transport Implementation)           |
 * +----------------------------------------------------------+
 * </pre>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.composedOf org.jboss.netty.channel.ChannelHandlerContext
 * @apiviz.owns       org.jboss.netty.channel.ChannelHandler
 * @apiviz.uses       org.jboss.netty.channel.ChannelSink - - sends events downstream
 */
public interface ChannelPipeline {
    void addFirst (String name, ChannelHandler handler);
    void addLast  (String name, ChannelHandler handler);
    void addBefore(String baseName, String name, ChannelHandler handler);
    void addAfter (String baseName, String name, ChannelHandler handler);

    void remove(ChannelHandler handler);
    ChannelHandler remove(String name);
    <T extends ChannelHandler> T remove(Class<T> handlerType);
    ChannelHandler removeFirst();
    ChannelHandler removeLast();

    void replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler);
    ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler);
    <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName, ChannelHandler newHandler);

    ChannelHandler getFirst();
    ChannelHandler getLast();

    ChannelHandler get(String name);
    <T extends ChannelHandler> T get(Class<T> handlerType);

    ChannelHandlerContext getContext(ChannelHandler handler);
    ChannelHandlerContext getContext(String name);
    ChannelHandlerContext getContext(Class<? extends ChannelHandler> handlerType);

    void sendUpstream(ChannelEvent e);
    void sendDownstream(ChannelEvent e);

    Channel getChannel();
    ChannelSink getSink();
    void attach(Channel channel, ChannelSink sink);

    Map<String, ChannelHandler> toMap();
}
