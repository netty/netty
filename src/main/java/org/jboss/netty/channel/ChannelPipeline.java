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
 * The following diagram describes how {@link ChannelEvent}s are processed by
 * {@link ChannelHandler}s in a {@link ChannelPipeline} typically:
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

    /**
     * Inserts a {@link ChannelHandler} at the first position of this pipeline.
     *
     * @param name     the name of the handler to insert first
     * @param handler  the handler to insert first
     */
    void addFirst (String name, ChannelHandler handler);

    /**
     * Appends a {@link ChannelHandler} at the last position of this pipeline.
     *
     * @param name     the name of the handler to append
     * @param handler  the handler to append
     */
    void addLast  (String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} before an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert before
     * @param handler   the handler to insert before
     */
    void addBefore(String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} after an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert after
     * @param handler   the handler to insert after
     */
    void addAfter (String baseName, String name, ChannelHandler handler);

    /**
     * Removes the specified {@link ChannelHandler} from this pipeline.
     */
    void remove(ChannelHandler handler);

    /**
     * Removes the {@link ChannelHandler} with the specified name from this
     * pipeline.
     *
     * @return the removed handler
     */
    ChannelHandler remove(String name);

    /**
     * Removes the {@link ChannelHandler} of the specified type from this
     * pipeline
     *
     * @param <T>          the type of the handler
     * @param handlerType  the type of the handler
     *
     * @return the removed handler
     */
    <T extends ChannelHandler> T remove(Class<T> handlerType);

    /**
     * Removes the first {@link ChannelHandler} in this pipeline.
     *
     * @return the removed handler
     */
    ChannelHandler removeFirst();

    /**
     * Removes the last {@link ChannelHandler} in this pipeline.
     *
     * @return the removed handler
     */
    ChannelHandler removeLast();

    /**
     * Replaces the specified {@link ChannelHandler} with a new handler in
     * this pipeline.
     */
    void replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified name with a new
     * handler in this pipeline.
     *
     * @return the removed handler
     */
    ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified type with a new
     * handler in this pipeline.
     *
     * @return the removed handler
     */
    <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName, ChannelHandler newHandler);

    /**
     * Returns the first {@link ChannelHandler} in this pipeline.
     */
    ChannelHandler getFirst();

    /**
     * Returns the last {@link ChannelHandler} in this pipeline.
     */
    ChannelHandler getLast();

    /**
     * Returns the {@link ChannelHandler} with the specified name in this
     * pipeline.
     */
    ChannelHandler get(String name);

    /**
     * Returns the {@link ChannelHandler} of the specified type in this
     * pipeline.
     */
    <T extends ChannelHandler> T get(Class<T> handlerType);

    /**
     * Returns the context object of the specified {@link ChannelHandler} in
     * this pipeline.
     */
    ChannelHandlerContext getContext(ChannelHandler handler);

    /**
     * Returns the context object of the {@link ChannelHandler} with the
     * specified name in this pipeline.
     */
    ChannelHandlerContext getContext(String name);

    /**
     * Returns the context object of the {@link ChannelHandler} of the
     * specified type in this pipeline.
     */
    ChannelHandlerContext getContext(Class<? extends ChannelHandler> handlerType);


    /**
     * Fires the specified {@link ChannelEvent} to the first
     * {@link ChannelUpstreamHandler} in this pipeline.
     */
    void sendUpstream(ChannelEvent e);

    /**
     * Fires the specified {@link ChannelEvent} to the last
     * {@link ChannelDownstreamHandler} in this pipeline.
     */
    void sendDownstream(ChannelEvent e);

    /**
     * Returns the {@link Channel} that this pipeline is attached to.
     */
    Channel getChannel();

    /**
     * Returns the {@link ChannelSink} that this pipeline is attached to.
     */
    ChannelSink getSink();

    /**
     * Attaches this pipeline to the specified {@link Channel} and
     * {@link ChannelSink}.  Once a pipeline is attached, it can't be detached
     * nor attached again.
     */
    void attach(Channel channel, ChannelSink sink);

    /**
     * Converts this pipeline into an ordered {@link Map} whose keys are
     * handler names and whose values are handlers.
     */
    Map<String, ChannelHandler> toMap();
}
