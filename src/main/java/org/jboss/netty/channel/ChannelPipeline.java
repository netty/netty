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
package org.jboss.netty.channel;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.NoSuchElementException;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.ssl.SslHandler;


/**
 * A list of {@link ChannelHandler}s which handles or intercepts
 * {@link ChannelEvent}s of a {@link Channel}.  {@link ChannelPipeline}
 * implements an advanced form of the
 * <a href="http://java.sun.com/blueprints/corej2eepatterns/Patterns/InterceptingFilter.html">Intercepting
 * Filter</a> pattern to give a user full control over how an event is handled
 * and how the {@link ChannelHandler}s in the pipeline interact with each other.
 *
 * <h3>Creation of a pipeline</h3>
 *
 * For each new channel, a new pipeline must be created and attached to the
 * channel.  Once attached, the coupling between the channel and the pipeline
 * is permanent; the channel cannot attach another pipeline to it nor detach
 * the current pipeline from it.
 * <p>
 * The recommended way to create a new pipeline is to use the helper methods in
 * {@link Channels} rather than calling an individual implementation's
 * constructor:
 * <pre>
 * import static org.jboss.netty.channel.{@link Channels}.*;
 * {@link ChannelPipeline} pipeline = pipeline(); // same with Channels.pipeline()
 * </pre>
 *
 * <h3>How an event flows in a pipeline</h3>
 *
 * The following diagram describes how {@link ChannelEvent}s are processed by
 * {@link ChannelHandler}s in a {@link ChannelPipeline} typically.
 * A {@link ChannelEvent} can be handled by either a {@link ChannelUpstreamHandler}
 * or a {@link ChannelDownstreamHandler} and be forwarded to the closest
 * handler by calling {@link ChannelHandlerContext#sendUpstream(ChannelEvent)}
 * or {@link ChannelHandlerContext#sendDownstream(ChannelEvent)}.  The meaning
 * of the event is interpreted somewhat differently depending on whether it is
 * going upstream or going downstream. Please refer to {@link ChannelEvent} for
 * more information.
 * <pre>
 *                                       I/O Request
 *                                     via {@link Channel} or
 *                                 {@link ChannelHandlerContext}
 *                                           |
 *  +----------------------------------------+---------------+
 *  |                  ChannelPipeline       |               |
 *  |                                       \|/              |
 *  |  +----------------------+  +-----------+------------+  |
 *  |  | Upstream Handler  N  |  | Downstream Handler  1  |  |
 *  |  +----------+-----------+  +-----------+------------+  |
 *  |            /|\                         |               |
 *  |             |                         \|/              |
 *  |  +----------+-----------+  +-----------+------------+  |
 *  |  | Upstream Handler N-1 |  | Downstream Handler  2  |  |
 *  |  +----------+-----------+  +-----------+------------+  |
 *  |            /|\                         .               |
 *  |             .                          .               |
 *  |     [ sendUpstream() ]        [ sendDownstream() ]     |
 *  |     [ + INBOUND data ]        [ + OUTBOUND data  ]     |
 *  |             .                          .               |
 *  |             .                         \|/              |
 *  |  +----------+-----------+  +-----------+------------+  |
 *  |  | Upstream Handler  2  |  | Downstream Handler M-1 |  |
 *  |  +----------+-----------+  +-----------+------------+  |
 *  |            /|\                         |               |
 *  |             |                         \|/              |
 *  |  +----------+-----------+  +-----------+------------+  |
 *  |  | Upstream Handler  1  |  | Downstream Handler  M  |  |
 *  |  +----------+-----------+  +-----------+------------+  |
 *  |            /|\                         |               |
 *  +-------------+--------------------------+---------------+
 *                |                         \|/
 *  +-------------+--------------------------+---------------+
 *  |             |                          |               |
 *  |     [ Socket.read() ]          [ Socket.write() ]      |
 *  |                                                        |
 *  |  Netty Internal I/O Threads (Transport Implementation) |
 *  +--------------------------------------------------------+
 * </pre>
 * An upstream event is handled by the upstream handlers in the bottom-up
 * direction as shown on the left side of the diagram.  An upstream handler
 * usually handles the inbound data generated by the I/O thread on the bottom
 * of the diagram.  The inbound data is often read from a remote peer via the
 * actual input operation such as {@link InputStream#read(byte[])}.
 * If an upstream event goes beyond the top upstream handler, it is discarded
 * silently.
 * <p>
 * A downstream event is handled by the downstream handler in the top-down
 * direction as shown on the right side of the diagram.  A downstream handler
 * usually generates or transforms the outbound traffic such as write requests.
 * If a downstream event goes beyond the bottom downstream handler, it is
 * handled by an I/O thread associated with the {@link Channel}. The I/O thread
 * often performs the actual output operation such as {@link OutputStream#write(byte[])}.
 * <p>
 * For example, let us assume that we created the following pipeline:
 * <pre>
 * {@link ChannelPipeline} p = {@link Channels}.pipeline();
 * p.addLast("1", new UpstreamHandlerA());
 * p.addLast("2", new UpstreamHandlerB());
 * p.addLast("3", new DownstreamHandlerA());
 * p.addLast("4", new DownstreamHandlerB());
 * p.addLast("5", new UpstreamHandlerX());
 * </pre>
 * In the example above, the class whose name starts with {@code Upstream} means
 * it is an upstream handler.  The class whose name starts with
 * {@code Downstream} means it is a downstream handler.
 * <p>
 * In the given example configuration, the handler evaluation order is 1, 2, 3,
 * 4, 5 when an event goes upstream.  When an event goes downstream, the order
 * is 5, 4, 3, 2, 1.  On top of this principle, {@link ChannelPipeline} skips
 * the evaluation of certain handlers to shorten the stack depth:
 * <ul>
 * <li>3 and 4 don't implement {@link ChannelUpstreamHandler}, and therefore the
 *     actual evaluation order of an upstream event will be: 1, 2, and 5.</li>
 * <li>1, 2, and 5 don't implement {@link ChannelDownstreamHandler}, and
 *     therefore the actual evaluation order of a downstream event will be:
 *     4 and 3.</li>
 * <li>If 5 extended {@link SimpleChannelHandler} which implements both
 *     {@link ChannelUpstreamHandler} and {@link ChannelDownstreamHandler}, the
 *     evaluation order of an upstream and a downstream event could be 125 and
 *     543 respectively.</li>
 * </ul>
 *
 * <h3>Building a pipeline</h3>
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
 * and it could be represented as shown in the following example:
 *
 * <pre>
 * {@link ChannelPipeline} pipeline = {@link Channels#pipeline() Channels.pipeline()};
 * pipeline.addLast("decoder", new MyProtocolDecoder());
 * pipeline.addLast("encoder", new MyProtocolEncoder());
 * pipeline.addLast("executor", new {@link ExecutionHandler}(new {@link OrderedMemoryAwareThreadPoolExecutor}(16, 1048576, 1048576)));
 * pipeline.addLast("handler", new MyBusinessLogicHandler());
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p>
 * A {@link ChannelHandler} can be added or removed at any time because a
 * {@link ChannelPipeline} is thread safe.  For example, you can insert a
 * {@link SslHandler} when sensitive information is about to be exchanged,
 * and remove it after the exchange.
 *
 * <h3>Pitfall</h3>
 * <p>
 * Due to the internal implementation detail of the current default
 * {@link ChannelPipeline}, the following code does not work as expected if
 * <tt>FirstHandler</tt> is the last handler in the pipeline:
 * <pre>
 * public class FirstHandler extends {@link SimpleChannelUpstreamHandler} {
 *
 *     {@code @Override}
 *     public void messageReceived({@link ChannelHandlerContext} ctx, {@link MessageEvent} e) {
 *         // Remove this handler from the pipeline,
 *         ctx.getPipeline().remove(this);
 *         // And let SecondHandler handle the current event.
 *         ctx.getPipeline().addLast("2nd", new SecondHandler());
 *         ctx.sendUpstream(e);
 *     }
 * }
 * </pre>
 * To implement the expected behavior, you have to add <tt>SecondHandler</tt>
 * before the removal or make sure there is at least one more handler between
 * <tt>FirstHandler</tt> and <tt>SecondHandler</tt>.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2153 $, $Date: 2010-02-17 17:24:25 +0900 (Wed, 17 Feb 2010) $
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
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified name or handler is {@code null}
     */
    void addFirst (String name, ChannelHandler handler);

    /**
     * Appends a {@link ChannelHandler} at the last position of this pipeline.
     *
     * @param name     the name of the handler to append
     * @param handler  the handler to append
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified name or handler is {@code null}
     */
    void addLast  (String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} before an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert before
     * @param handler   the handler to insert before
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName, name, or handler is {@code null}
     */
    void addBefore(String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} after an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert after
     * @param handler   the handler to insert after
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName, name, or handler is {@code null}
     */
    void addAfter (String baseName, String name, ChannelHandler handler);

    /**
     * Removes the specified {@link ChannelHandler} from this pipeline.
     *
     * @throws NoSuchElementException
     *         if there's no such handler in this pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    void remove(ChannelHandler handler);

    /**
     * Removes the {@link ChannelHandler} with the specified name from this
     * pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if there's no such handler with the specified name in this pipeline
     * @throws NullPointerException
     *         if the specified name is {@code null}
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
     *
     * @throws NoSuchElementException
     *         if there's no such handler of the specified type in this pipeline
     * @throws NullPointerException
     *         if the specified handler type is {@code null}
     */
    <T extends ChannelHandler> T remove(Class<T> handlerType);

    /**
     * Removes the first {@link ChannelHandler} in this pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if this pipeline is empty
     */
    ChannelHandler removeFirst();

    /**
     * Removes the last {@link ChannelHandler} in this pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if this pipeline is empty
     */
    ChannelHandler removeLast();

    /**
     * Replaces the specified {@link ChannelHandler} with a new handler in
     * this pipeline.
     *
     * @throws NoSuchElementException
     *         if the specified old handler does not exist in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler, new name, or new handler is
     *         {@code null}
     */
    void replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified name with a new
     * handler in this pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if the handler with the specified old name does not exist in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler, new name, or new handler is
     *         {@code null}
     */
    ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified type with a new
     * handler in this pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if the handler of the specified old handler type does not exist
     *         in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler, new name, or new handler is
     *         {@code null}
     */
    <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName, ChannelHandler newHandler);

    /**
     * Returns the first {@link ChannelHandler} in this pipeline.
     *
     * @return the first handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler getFirst();

    /**
     * Returns the last {@link ChannelHandler} in this pipeline.
     *
     * @return the last handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler getLast();

    /**
     * Returns the {@link ChannelHandler} with the specified name in this
     * pipeline.
     *
     * @return the handler with the specified name.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandler get(String name);

    /**
     * Returns the {@link ChannelHandler} of the specified type in this
     * pipeline.
     *
     * @return the handler of the specified handler type.
     *         {@code null} if there's no such handler in this pipeline.
     */
    <T extends ChannelHandler> T get(Class<T> handlerType);

    /**
     * Returns the context object of the specified {@link ChannelHandler} in
     * this pipeline.
     *
     * @return the context object of the specified handler.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext getContext(ChannelHandler handler);

    /**
     * Returns the context object of the {@link ChannelHandler} with the
     * specified name in this pipeline.
     *
     * @return the context object of the handler with the specified name.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext getContext(String name);

    /**
     * Returns the context object of the {@link ChannelHandler} of the
     * specified type in this pipeline.
     *
     * @return the context object of the handler of the specified type.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext getContext(Class<? extends ChannelHandler> handlerType);


    /**
     * Sends the specified {@link ChannelEvent} to the first
     * {@link ChannelUpstreamHandler} in this pipeline.
     *
     * @throws NullPointerException
     *         if the specified event is {@code null}
     */
    void sendUpstream(ChannelEvent e);

    /**
     * Sends the specified {@link ChannelEvent} to the last
     * {@link ChannelDownstreamHandler} in this pipeline.
     *
     * @throws NullPointerException
     *         if the specified event is {@code null}
     */
    void sendDownstream(ChannelEvent e);

    /**
     * Returns the {@link Channel} that this pipeline is attached to.
     *
     * @return the channel. {@code null} if this pipeline is not attached yet.
     */
    Channel getChannel();

    /**
     * Returns the {@link ChannelSink} that this pipeline is attached to.
     *
     * @return the sink. {@code null} if this pipeline is not attached yet.
     */
    ChannelSink getSink();

    /**
     * Attaches this pipeline to the specified {@link Channel} and
     * {@link ChannelSink}.  Once a pipeline is attached, it can't be detached
     * nor attached again.
     *
     * @throws IllegalStateException if this pipeline is attached already
     */
    void attach(Channel channel, ChannelSink sink);

    /**
     * Returns {@code true} if and only if this pipeline is attached to
     * a {@link Channel}.
     */
    boolean isAttached();

    /**
     * Converts this pipeline into an ordered {@link Map} whose keys are
     * handler names and whose values are handlers.
     */
    Map<String, ChannelHandler> toMap();
}
